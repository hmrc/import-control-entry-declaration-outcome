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

package uk.gov.hmrc.entrydeclarationoutcome.repositories

import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import org.mongodb.scala.model.Filters._
import scala.concurrent.{ExecutionContext, Future}

trait RepositoryFns {
  this: PlayMongoRepository[_] =>

  def removeAll()(implicit ec: ExecutionContext): Future[Unit] =
    collection
      .deleteMany(exists("_id"))
      .toFutureOption
      .map( _ => ())

  def count()(implicit ec: ExecutionContext): Future[Long] =
    collection
      .countDocuments(exists("_id"))
      .toFutureOption
      .map{
        case None => 0L
        case Some(count) => count
      }
}