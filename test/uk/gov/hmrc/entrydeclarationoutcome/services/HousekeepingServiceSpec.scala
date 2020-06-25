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

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationoutcome.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationoutcome.repositories.MockOutcomeRepo
import uk.gov.hmrc.play.test.UnitSpec

class HousekeepingServiceSpec extends UnitSpec with MockOutcomeRepo with ScalaFutures {

  val service = new HousekeepingService(outcomeRepo)

  "HousekeepingService" when {
    "getting housekeeping status" must {
      "get using the repo" in {
        // WLOG
        val status = HousekeepingStatus.On

        MockOutcomeRepo.getHousekeepingStatus returns status
        service.getHousekeepingStatus.futureValue shouldBe status
      }
    }

    "setting housekeeping status" must {
      "set using the repo" in {
        // WLOG
        val success = true
        val value   = false

        MockOutcomeRepo.enableHousekeeping(value) returns success
        service.enableHousekeeping(value).futureValue shouldBe success
      }
    }
  }

}
