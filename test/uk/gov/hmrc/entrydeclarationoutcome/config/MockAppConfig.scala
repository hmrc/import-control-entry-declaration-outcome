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

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.FiniteDuration

trait MockAppConfig extends MockFactory {
  val mockAppConfig: AppConfig = mock[AppConfig]

  object MockAppConfig {
    def appName: CallHandler[String] = mockAppConfig.appName _ expects ()

    def eventsHost: CallHandler[String] = mockAppConfig.eventsHost _ expects ()

    def apiSubscriptionFieldsHost: CallHandler[String] = mockAppConfig.apiSubscriptionFieldsHost _ expects ()

    def apiGatewayContext: CallHandler[String] = mockAppConfig.apiGatewayContext _ expects ()

    def apiStatus: CallHandler[String] = mockAppConfig.apiStatus _ expects ()

    def apiEndpointsEnabled: CallHandler[Boolean] = mockAppConfig.apiEndpointsEnabled _ expects ()

    def listOutcomesLimit: CallHandler[Int] = mockAppConfig.listOutcomesLimit _ expects ()

    def shortTtl: CallHandler[FiniteDuration] = mockAppConfig.shortTtl _ expects ()
  }
}
