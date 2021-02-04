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

import org.scalamock.handlers.CallHandler
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthProvider, AuthProviders, Enrolment, EnrolmentIdentifier, Enrolments, InsufficientEnrolments}
import uk.gov.hmrc.entrydeclarationoutcome.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationoutcome.connectors.{MockApiSubscriptionFieldsConnector, MockAuthConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class AuthServiceSpec
    extends UnitSpec
    with MockAuthConnector
    with MockApiSubscriptionFieldsConnector
    with ScalaFutures
    with MockAppConfig
    with Inside {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(500, Millis))

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val service  = new AuthService(mockAuthConnector, mockApiSubscriptionFieldsConnector, mockAppConfig)
  val eori     = "GB123"
  val clientId = "someClientId"

  // WLOG - any AuthorisationException will do
  val authException = new InsufficientEnrolments with NoStackTrace

  val enrolmentKey = "HMRC-SS-ORG"
  val identifier   = "EORINumber"

  def validICSEnrolment(eori: String): Enrolment =
    Enrolment(
      key               = enrolmentKey,
      identifiers       = Seq(EnrolmentIdentifier(identifier, eori)),
      state             = "Activated",
      delegatedAuthRule = None)

  def stubAuth(implicit hc: HeaderCarrier): CallHandler[Future[Enrolments]] =
    MockAuthConnector
      .authorise(AuthProviders(AuthProvider.GovernmentGateway), Retrievals.allEnrolments, hc)

  def stubCSPAuth(implicit hc: HeaderCarrier): CallHandler[Future[Unit]] =
    MockAuthConnector.authorise(AuthProviders(AuthProvider.PrivilegedApplication), EmptyRetrieval, hc)

  "AuthService.authenticate" when {
    "X-Client-Id header present" when {
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("X-Client-Id" -> clientId)

      "CSP authentication succeeds" when {
        "authenticated EORI present in subscription fields" should {
          "return that EORI" in {

            stubCSPAuth returns Future.successful(())

            MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Some(eori)
            service.authenticate().futureValue shouldBe Some(eori)
          }
        }

        "no authenticated EORI present in subscription fields" should {
          "return None (without non-CSP auth)" in {
            stubCSPAuth returns Future.successful(())
            MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns None

            service.authenticate().futureValue shouldBe None
          }
        }
      }

      "CSP authentication fails" should {
        authenticateBasedOnSsEnrolment { () =>
          stubCSPAuth returns Future.failed(authException)
        }
      }
    }

    "no X-Client-Id header present" should {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      authenticateBasedOnSsEnrolment { () =>
        }
    }

    "X-Client-Id header present with different case" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("x-client-id" -> clientId)

      "Attempt CSP auth" in {
        stubCSPAuth returns Future.successful(())

        MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Some(eori)
        service.authenticate().futureValue shouldBe Some(eori)
      }
    }

    def authenticateBasedOnSsEnrolment(stubbings: () => Unit)(implicit hc: HeaderCarrier): Unit = {
      "return Some(eori)" when {
        "SS enrolment with an eori" in {
          stubbings()
          stubAuth returns Enrolments(Set(validICSEnrolment(eori)))
          service.authenticate().futureValue shouldBe Some(eori)
        }
      }

      "return None" when {
        "SS enrolment with no identifiers" in {
          stubbings()
          stubAuth returns Enrolments(Set(validICSEnrolment(eori).copy(identifiers = Nil)))
          service.authenticate().futureValue shouldBe None
        }

        "no SS enrolment in authorization header" in {
          stubbings()
          stubAuth returns Enrolments(
            Set(
              Enrolment(
                key               = "OTHER",
                identifiers       = Seq(EnrolmentIdentifier(identifier, eori)),
                state             = "Activated",
                delegatedAuthRule = None)))
          service.authenticate().futureValue shouldBe None
        }
        "no enrolments at all in authorization header" in {
          stubbings()
          stubAuth returns Enrolments(Set.empty)
          service.authenticate().futureValue shouldBe None
        }

        "SS enrolment not activated" in {
          stubbings()
          stubAuth returns Enrolments(Set(validICSEnrolment(eori).copy(state = "inactive")))
          service.authenticate().futureValue shouldBe None
        }

        "authorisation fails" in {
          stubbings()
          stubAuth returns Future.failed(authException)
          service.authenticate().futureValue shouldBe None
        }
      }
    }
  }
}
