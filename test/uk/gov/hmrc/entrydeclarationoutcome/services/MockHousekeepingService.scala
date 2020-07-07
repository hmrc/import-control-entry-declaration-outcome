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

package uk.gov.hmrc.entrydeclarationoutcome.services

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationoutcome.models.HousekeepingStatus

import scala.concurrent.Future

trait MockHousekeepingService extends MockFactory {
  val mockHousekeepingService: HousekeepingService = mock[HousekeepingService]

  object MockHousekeepingService {
    def enableHousekeeping(value: Boolean): CallHandler[Future[Boolean]] =
      mockHousekeepingService.enableHousekeeping _ expects value

    def getHousekeepingStatus: CallHandler[Future[HousekeepingStatus]] =
      mockHousekeepingService.getHousekeepingStatus _ expects ()
  }
}