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

package uk.gov.hmrc.entrydeclarationoutcome.models

import play.api.libs.json.Format
import uk.gov.hmrc.entrydeclarationoutcome.utils.Enums


sealed trait MessageType

object MessageType {

  case object IE328 extends MessageType

  case object IE304 extends MessageType

  case object IE316 extends MessageType

  case object IE305 extends MessageType

  implicit val formats: Format[MessageType] = Enums.format[MessageType]
}
