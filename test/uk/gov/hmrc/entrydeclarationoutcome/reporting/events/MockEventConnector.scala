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

package uk.gov.hmrc.entrydeclarationoutcome.reporting.events

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait MockEventConnector extends MockFactory {
  val mockEventConnector: EventConnector = mock[EventConnector]

  object MockEventConnector {
    def sendEvent(event: Event): CallHandler[Future[Unit]] =
      (mockEventConnector.sendEvent(_: Event)(_: HeaderCarrier, _: LoggingContext)) expects (event, *, *)
  }
}
