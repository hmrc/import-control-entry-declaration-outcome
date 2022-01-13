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

package uk.gov.hmrc.entrydeclarationoutcome.services

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, OutcomeMetadata, OutcomeReceived, OutcomeXml}

import scala.concurrent.Future

trait MockOutcomeRetrievalService extends MockFactory {
  val mockOutcomeXmlRetrievalService: OutcomeRetrievalService = mock[OutcomeRetrievalService]

  object MockOutcomeXmlRetrievalService {
    def retrieveOutcome(submissionId: String): CallHandler[Future[Option[OutcomeXml]]] =
      (mockOutcomeXmlRetrievalService.retrieveOutcomeXml(_: String)) expects submissionId

    def retrieveOutcome(eori: String, correlationId: String): CallHandler[Future[Option[OutcomeReceived]]] =
      (mockOutcomeXmlRetrievalService.retrieveOutcome(_: String, _: String)) expects (eori, correlationId)

    def retrieveFullOutcome(eori: String, correlationId: String): CallHandler[Future[Option[FullOutcome]]] =
      (mockOutcomeXmlRetrievalService.retrieveFullOutcome(_: String, _: String)) expects (eori, correlationId)

    def acknowledgeOutcome(eori: String, correlationId: String): CallHandler[Future[Option[OutcomeReceived]]] =
      (mockOutcomeXmlRetrievalService
        .acknowledgeOutcome(_: String, _: String)(_: LoggingContext)) expects (eori, correlationId, *)

    def listOutcomes(eori: String): CallHandler[Future[List[OutcomeMetadata]]] =
      mockOutcomeXmlRetrievalService.listOutcomes _ expects eori
  }

}
