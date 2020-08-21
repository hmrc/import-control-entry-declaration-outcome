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

import controllers.Assets
import javax.inject.{Inject, Singleton}
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class DocumentationController @Inject()(
  cc: ControllerComponents,
  assets: Assets,
  appConfig: AppConfig,
  errorHandler: HttpErrorHandler)
    extends BackendController(cc) {

  def documentation(version: String, endpointName: String): Action[AnyContent] =
    assets.at(s"/public/api/documentation/$version", s"${endpointName.replaceAll(" ", "-")}.xml")

  def definition(): Action[AnyContent] = Action {
    Ok(Json.parse(s"""{
                     |  "scopes": [
                     |    {
                     |      "key": "write:import-control-system",
                     |      "name": "Access Import Control System",
                     |      "description": "Allow access to entry summary declaration outcomes"
                     |    }
                     |  ],
                     |  "api": {
                     |    "name": "Safety and Security Import Outcomes",
                     |    "description": "API with endpoints for getting outcomes of Entry Summary Declarations.",
                     |    "context": "${appConfig.apiGatewayContext}",
                     |    "categories": [
                     |      "CUSTOMS"
                     |    ],
                     |    "versions": [
                     |      {
                     |        "version": "1.0",
                     |        "status": "${appConfig.apiStatus}",
                     |        "endpointsEnabled": ${appConfig.apiEndpointsEnabled},
                     |        "fieldDefinitions": [
                     |          {
                     |            "name": "authenticatedEori",
                     |            "description": "What's your Economic Operator Registration and Identification (EORI) number?",
                     |            "type": "STRING",
                     |            "hint": "This is your EORI that will associate your application with you as a CSP"
                     |          }
                     |        ]
                     |      }
                     |    ]
                     |  }
                     |}""".stripMargin))
  }

  def conf(version: String, file: String): Action[AnyContent] =
    assets.at(s"/public/api/conf/$version", file)
}
