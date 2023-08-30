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

package uk.gov.hmrc.entrydeclarationoutcome.repositories

import javax.inject.{Inject, Singleton}
import play.api.Logger
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.entrydeclarationoutcome.models.HousekeepingStatus
import uk.gov.hmrc.play.http.logging.Mdc
import scala.concurrent.{ExecutionContext, Future}

trait HousekeepingRepo {
  def enableHousekeeping(value: Boolean): Future[Unit]
  def getHousekeepingStatus: Future[HousekeepingStatus]
}

@Singleton
class HousekeepingRepoImpl @Inject()(
  implicit mongo: MongoComponent,
  ec: ExecutionContext
) extends PlayMongoRepository[HousekeepingStatus] (
  collectionName = "houskeeping-status",
  mongoComponent = mongo,
  domainFormat = HousekeepingStatus.format,
  indexes = Seq.empty,
  extraCodecs = Seq.empty,
  replaceIndexes = false)
    with HousekeepingRepo with RepositoryFns {
  private val logger: Logger = Logger(getClass)
  private val singletonId = "1d4165fc-3a66-4f13-b067-ac7e087aab73"

  override def enableHousekeeping(value: Boolean): Future[Unit] =
    if (value) turnOn() else turnOff()

  private def turnOn() =
    Mdc.preservingMdc(
      collection
        .deleteOne(equal("_id", singletonId))
        .toFutureOption
    )
    .map{
      case None => ()
      case Some(_) =>
        logger.warn("Housekeeping turned on")
        ()
    }

  private def turnOff() =
    Mdc
      .preservingMdc(
        collection
          .findOneAndUpdate(
            equal("_id", singletonId),
            set("on", false),
            FindOneAndUpdateOptions().upsert(true)
          )
          .toFutureOption
      )
      .map{
        case None => ()
        case Some(_) =>
          logger.warn("Housekeeping turned off")
          ()
      }

  override def getHousekeepingStatus: Future[HousekeepingStatus] =
    Mdc.preservingMdc(
      collection
        .countDocuments(equal("_id", singletonId))
        .headOption
    )
    .map{
      case None => HousekeepingStatus(true)
      case Some(count) => HousekeepingStatus(count == 0)
    }
}
