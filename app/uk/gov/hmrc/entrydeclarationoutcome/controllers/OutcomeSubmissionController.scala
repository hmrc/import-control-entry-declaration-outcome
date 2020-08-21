/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, JsValue}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.entrydeclarationoutcome.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationoutcome.models.OutcomeReceived
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.EventCode
import uk.gov.hmrc.entrydeclarationoutcome.reporting.{OutcomeReport, ReportSender}
import uk.gov.hmrc.entrydeclarationoutcome.services.OutcomeSubmissionService
import uk.gov.hmrc.entrydeclarationoutcome.utils.{EventLogger, SaveError}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutcomeSubmissionController @Inject()(
  cc: ControllerComponents,
  service: OutcomeSubmissionService,
  reportSender: ReportSender
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with EventLogger {

  val postOutcome: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[OutcomeReceived] match {
      case JsSuccess(outcomeReceived, _) =>
        implicit val lc: LoggingContext =
          LoggingContext(
            eori          = outcomeReceived.eori,
            correlationId = outcomeReceived.correlationId,
            submissionId  = outcomeReceived.submissionId,
            outcomeReceived.movementReferenceNumber,
            outcomeReceived.messageType
          )

        ContextLogger.info("Received outcome")

        service
          .saveOutcome(outcomeReceived)
          .map {
            case Some(SaveError.Duplicate) =>
              ContextLogger.warn(s"Duplicate Outcome already exists")
              Created
            case Some(SaveError.ServerError) =>
              ContextLogger.warn(s"Unable to persist Outcome due to ServerError")
              InternalServerError
            case None =>
              ContextLogger.info("Outcome created")
              reportSender.sendReport(OutcomeReport(outcomeReceived, EventCode.ENS_RESP_READY))
              Created
          }

      case JsError(errs) =>
        Logger.error(s"Unable to parse payload: $errs")
        Future.successful(BadRequest)
    }
  }
}
