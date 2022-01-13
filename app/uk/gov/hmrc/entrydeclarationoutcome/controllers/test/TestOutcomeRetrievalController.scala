/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.controllers.test

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationoutcome.services.OutcomeRetrievalService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class TestOutcomeRetrievalController @Inject()(cc: ControllerComponents, service: OutcomeRetrievalService)(
  implicit ec: ExecutionContext)
    extends BackendController(cc) {
//  Test method -> no auth
  def getOutcomeXmlBySubmissionId(submissionId: String): Action[AnyContent] = Action.async { _ =>
    service.retrieveOutcomeXml(submissionId) map {
      case Some(outcomeXml) => Ok(outcomeXml.value).as(MimeTypes.XML)
      case None             => NotFound
    }
  }

  //  Test method -> no auth
  def getFullOutcome(eori: String, correlationId: String): Action[AnyContent] = Action.async { _ =>
    service.retrieveFullOutcome(eori, correlationId) map {
      case Some(fullOutcome) => Ok(Json.toJson(fullOutcome))
      case None              => NotFound
    }
  }
}
