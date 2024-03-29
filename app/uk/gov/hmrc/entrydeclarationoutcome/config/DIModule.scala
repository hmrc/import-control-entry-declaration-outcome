/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.config

import java.time.Clock

import org.apache.pekko.actor.{ActorSystem, Scheduler}
import com.google.inject.{AbstractModule, Provides}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.entrydeclarationoutcome.housekeeping.{Housekeeper, HousekeepingScheduler}
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.{EventConnector, EventConnectorImpl}
import uk.gov.hmrc.entrydeclarationoutcome.repositories.{HousekeepingRepo, HousekeepingRepoImpl, OutcomeRepo, OutcomeRepoImpl}
import uk.gov.hmrc.entrydeclarationoutcome.services.HousekeepingService
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

class DIModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[AppConfig]).to(classOf[AppConfigImpl]).asEagerSingleton()
    bind(classOf[HousekeepingScheduler]).asEagerSingleton()
    bind(classOf[Housekeeper]).to(classOf[HousekeepingService])
    bind(classOf[HousekeepingRepo]).to(classOf[HousekeepingRepoImpl])
    bind(classOf[OutcomeRepo]).to(classOf[OutcomeRepoImpl])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[EventConnector]).to(classOf[EventConnectorImpl])
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
  }

  @Provides
  def pekkoScheduler(actorSystem: ActorSystem): Scheduler =
    actorSystem.scheduler
}
