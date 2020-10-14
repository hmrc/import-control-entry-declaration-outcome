package uk.gov.hmrc.entrydeclarationoutcome.repositories
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationoutcome.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationoutcome.models.HousekeepingStatus

import scala.concurrent.ExecutionContext.Implicits.global

class HousekeepingRepoISpec
  extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with Eventually
    with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false")
    .disable[HousekeepingScheduler]
    .build()

  lazy val repository: HousekeepingRepoImpl = inject[HousekeepingRepoImpl]

  "HousekeepingRepo" when {
    "off indicator document is not present" when {

      trait Scenario {
        await(repository.removeAll())
      }

      "getHousekeepingStatus" must {
        "return state as on" in new Scenario {
          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
        }
      }

      "turn on" must {
        "do nothing" in new Scenario {
          await(repository.enableHousekeeping(true))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
          await(repository.count)                 shouldBe 0
        }
      }

      "turn off" must {
        "add off indicator document" in new Scenario {
          await(repository.enableHousekeeping(false))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
          await(repository.count)                 shouldBe 1
        }
      }
    }

    "database has off indicator document" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.enableHousekeeping(false))
      }

      "getHousekeepingStatus" must {
        "report state as off" in new Scenario {
          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
        }
      }

      "turn off" must {
        "do nothing" in new Scenario {
          await(repository.enableHousekeeping(false))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
          await(repository.count)                 shouldBe 1
        }
      }

      "turn on" must {
        "remove the off indicator document" in new Scenario {
          await(repository.enableHousekeeping(true))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
          await(repository.count)                 shouldBe 0
        }
      }
    }
  }

}