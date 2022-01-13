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

package uk.gov.hmrc.entrydeclarationoutcome.models

import scala.xml.Node

/**
 * Error that conforms to the standard API platform error structure.
 */

object StandardError {
  val notFound: Node =
  // @formatter:off
    <error>
      <code>OUTCOME_NOT_FOUND</code>
      <message>No unacknowledged outcome found</message>
    </error>
  // @formatter:on

  val unauthorised: Node =
  // @formatter:off
    <error>
      <code>UNAUTHORIZED</code>
      <message>Permission denied</message>
    </error>
  // @formatter:on

}
