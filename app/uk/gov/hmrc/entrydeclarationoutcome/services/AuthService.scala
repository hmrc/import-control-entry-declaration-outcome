/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.entrydeclarationoutcome.services

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject()(
  val authConnector: AuthConnector,
  apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector,
  appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions {

  private val X_CLIENT_ID = "X-Client-Id"

  type Eori = String

  sealed trait AuthError
  case object NoClientId extends AuthError
  case object NoEori extends AuthError
  case object AuthFail extends AuthError

  def authenticate()(implicit hc: HeaderCarrier): Future[Option[Eori]] =
    authCSP
      .recoverWith {
        case AuthFail | NoClientId => authNonCSP
      }
      .toOption
      .value

  private def authCSP(implicit hc: HeaderCarrier): EitherT[Future, AuthError, Eori] = {
    def auth: Future[Option[Unit]] =
      authorised(AuthProviders(AuthProvider.PrivilegedApplication))
        .retrieve(EmptyRetrieval) { _ =>
          Logger.debug(s"Successfully authorised CSP PrivilegedApplication")
          Future.successful(Some(()))
        }
        .recover {
          case ae: AuthorisationException =>
            Logger.debug(s"No authorisation for CSP PrivilegedApplication", ae)
            None
        }

    for {
      clientId <- EitherT.fromOption[Future](hc.headers.find(_._1.equalsIgnoreCase(X_CLIENT_ID)).map(_._2), NoClientId)
      _        <- EitherT.fromOptionF(auth, AuthFail)
      eori     <- EitherT.fromOptionF(apiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId), NoEori: AuthError)
    } yield eori
  }

  private def authNonCSP(implicit hc: HeaderCarrier): EitherT[Future, AuthError, Eori] =
    EitherT(authorised(AuthProviders(AuthProvider.GovernmentGateway))
      .retrieve(allEnrolments) { usersEnrolments =>
        val ssEnrolments =
          usersEnrolments.enrolments.filter(enrolment => enrolment.isActivated && enrolment.key == "HMRC-SS-ORG")

        val eoris = for {
          enrolment <- ssEnrolments
          eoriId    <- enrolment.getIdentifier("EORINumber")
        } yield eoriId.value

        val eori = eoris.headOption

        val result = eori match {
          case Some(eori) => eori.asRight
          case None       => NoEori.asLeft
        }

        Logger.debug(
          s"Successfully authorised non-CSP GovernmentGateway with enrolments ${usersEnrolments.enrolments} and eori $eori")
        Future.successful(result)
      }
      .recover {
        case ae: AuthorisationException =>
          Logger.debug(s"No authorisation for non-CSP GovernmentGateway", ae)
          AuthFail.asLeft
      })
}
