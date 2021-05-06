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

package uk.gov.hmrc.entrydeclarationoutcome.controllers

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationoutcome.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationoutcome.models.{OutcomeMetadata, StandardError}
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.EventCode
import uk.gov.hmrc.entrydeclarationoutcome.reporting.{OutcomeReport, ReportSender}
import uk.gov.hmrc.entrydeclarationoutcome.services.{AuthService, OutcomeRetrievalService}

import scala.concurrent.ExecutionContext

class OutcomeRetrievalController @Inject()(
  val authService: AuthService,
  cc: ControllerComponents,
  service: OutcomeRetrievalService,
  reportSender: ReportSender)(implicit ec: ExecutionContext)
    extends AuthorisedController(cc) {

  def listOutcomes(): Action[AnyContent] = authorisedAction().async { userRequest =>
    implicit val lc: LoggingContext = LoggingContext(eori = Some(userRequest.eori))

    ContextLogger.info("Listing outcomes")

    service.listOutcomes(userRequest.eori).map {
      case Nil             => NoContent
      case outcomeMetadata => Ok(listXml(outcomeMetadata)).as(MimeTypes.XML)
    }
  }

  def getOutcome(correlationId: String): Action[AnyContent] = authorisedAction().async { implicit userRequest =>
    implicit val lc: LoggingContext = LoggingContext(eori = Some(userRequest.eori), correlationId = Some(correlationId))

    ContextLogger.info("Fetching outcome")

    service.retrieveOutcome(userRequest.eori, correlationId) map {
      case Some(outcome) =>
        ContextLogger.info("Outcome fetched")
        reportSender.sendReport(OutcomeReport(outcome, EventCode.ENS_RESP_COLLECTED))
        Ok(outcome.outcomeXml).as(MimeTypes.XML)
      case None =>
        ContextLogger.info("Outcome not found")
        NotFound(StandardError.notFound).as(MimeTypes.XML)
    }
  }

  def acknowledgeOutcome(correlationId: String): Action[AnyContent] = authorisedAction().async { implicit userRequest =>
    implicit val lc: LoggingContext = LoggingContext(eori = Some(userRequest.eori), correlationId = Some(correlationId))

    ContextLogger.info("Acknowledging outcome")

    service.acknowledgeOutcome(userRequest.eori, correlationId) map {
      case Some(outcome) =>
        ContextLogger.info("Outcome acknowledged")
        reportSender.sendReport(OutcomeReport(outcome, EventCode.ENS_RESP_ACK))
        Ok
      case None =>
        ContextLogger.info("Outcome not found")
        NotFound(StandardError.notFound).as(MimeTypes.XML)
    }
  }

  private def listXml(outcomes: List[OutcomeMetadata]): String = {
    val width: Int = 100
    val step: Int = 4

    val prettyPrinter = new scala.xml.PrettyPrinter(width, step)

    val xml = <entryDeclarationResponses>{outcomes.map { outcomeMetadata =>
      <response>
        <correlationId>{outcomeMetadata.correlationId}</correlationId>
        <link>/customs/imports/outcomes/{outcomeMetadata.correlationId}</link>
        {for (value <- outcomeMetadata.movementReferenceNumber.toSeq) yield <MRN>{value}</MRN>}
      </response>
    }}</entryDeclarationResponses>

    prettyPrinter.format(xml)
  }
}
