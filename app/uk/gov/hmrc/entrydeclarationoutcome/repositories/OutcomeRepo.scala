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

package uk.gov.hmrc.entrydeclarationoutcome.repositories

import java.time.Instant

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsObject, JsPath, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.Command
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, ReadPreference, WriteConcern}
import reactivemongo.bson.{BSONDocument, BSONObjectID, _}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationoutcome.models.{HousekeepingStatus, OutcomeMetadata, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

trait OutcomeRepo {
  def save(outcome: OutcomeReceived)(implicit lc: LoggingContext): Future[Option[SaveError]]

  def lookupOutcomeXml(submissionId: String): Future[Option[OutcomeXml]]

  def lookupOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]]

  /**
    * @return the acknowledged outcome
    */
  def acknowledgeOutcome(eori: String, correlationId: String)(
    implicit lc: LoggingContext): Future[Option[OutcomeReceived]]

  def listOutcomes(eori: String): Future[List[OutcomeMetadata]]

  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]

  def enableHousekeeping(value: Boolean): Future[Boolean]

  def getHousekeepingStatus: Future[HousekeepingStatus]
}

@Singleton
class OutcomeRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[OutcomePersisted, BSONObjectID](
      "outcome",
      mongo.mongoConnector.db,
      OutcomePersisted.format,
      ReactiveMongoFormats.objectIdFormats)
    with OutcomeRepo {

  private val housekeepingOnTTLSecs  = 0
  private val housekeepingOffTTLSecs = Long.MaxValue

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("submissionId", Ascending)), name = Some("submissionIdIndex"), unique = true),
    //TTL index
    Index(
      Seq("housekeepingAt" -> Ascending),
      name    = Some("housekeepingIndex"),
      options = BSONDocument("expireAfterSeconds" -> 0)),
    // Covering index for list...
    Index(
      Seq(
        ("eori", Ascending),
        ("acknowledged", Ascending),
        ("receivedDateTime", Ascending),
        ("correlationId", Ascending),
        ("movementReferenceNumber", Ascending)),
      name   = Some("listIndex"),
      unique = false
    ),
    Index(
      Seq(("eori", Ascending), ("correlationId", Ascending)),
      name   = Some("eoriPlusCorrelationIdIndex"),
      unique = true
    )
  )

  val mongoErrorCodeForDuplicate: Int = 11000

  def save(outcome: OutcomeReceived)(implicit lc: LoggingContext): Future[Option[SaveError]] = {
    val outcomePersisted = OutcomePersisted.from(outcome)

    insert(outcomePersisted)
      .map(_ => None)
      .recover {
        case e: DatabaseException =>
          import outcome._

          if (e.code.contains(mongoErrorCodeForDuplicate)) {
            ContextLogger.error(s"Duplicate entry declaration outcome", e)
            Some(SaveError.Duplicate)
          } else {
            ContextLogger.error(s"Unable to save entry declaration outcome", e)
            Some(SaveError.ServerError)
          }
      }
  }

  //Test only -> so can retrieve even if acknowledged
  def lookupOutcomeXml(submissionId: String): Future[Option[OutcomeXml]] =
    collection
      .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("outcomeXml" -> 1)))
      .one[OutcomeXml]

  def lookupOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]] =
    collection
      .find(Json.obj("eori" -> eori, "correlationId" -> correlationId, "acknowledged" -> false), Option.empty[JsObject])
      .one[OutcomePersisted]
      .map(_.map(_.toOutcomeReceived))

  def acknowledgeOutcome(eori: String, correlationId: String)(
    implicit lc: LoggingContext): Future[Option[OutcomeReceived]] =
    findAndUpdate(
      query          = Json.obj("eori" -> eori, "correlationId" -> correlationId, "acknowledged" -> false),
      update         = Json.obj("$set" -> Json.obj("acknowledged" -> true)),
      fetchNewObject = true
    ).map(result => result.result[OutcomePersisted].map(_.toOutcomeReceived))
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to acknowledge outcome", e)
          None
      }

  def listOutcomes(eori: String): Future[List[OutcomeMetadata]] =
    collection
      .find(
        Json.obj("eori" -> eori, "acknowledged" -> false),
        Some(Json.obj("correlationId" -> 1, "movementReferenceNumber" -> 1)))
      .sort(Json.obj("receivedDateTime" -> 1))
      .cursor[OutcomeMetadata]()
      .collect[List](maxDocs = appConfig.listOutcomesLimit, err = Cursor.FailOnError[List[OutcomeMetadata]]())

  override def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        Json.obj("submissionId" -> submissionId),
        Json.obj("$set"         -> Json.obj("housekeepingAt" -> PersistableDateTime(time)))
      )
      .map(result => result.n == 1)

  // If pausing housekeeping is exposed as a simple switch,
  // using collMod would seem more effective than a full index re-build when it is toggled on or off. See
  // https://dba.stackexchange.com/questions/123761/drop-create-mongodb-ttl-index-vs-collmod-in-production
  override def enableHousekeeping(value: Boolean): Future[Boolean] = {
    val ttlSecs = if (value) housekeepingOnTTLSecs else housekeepingOffTTLSecs

    val commandDoc = Json.obj(
      "collMod" -> "outcome",
      "index"   -> Json.obj("keyPattern" -> Json.obj("housekeepingAt" -> 1), "expireAfterSeconds" -> ttlSecs))

    val runner = Command.CommandWithPackRunner(JSONSerializationPack)
    runner(mongo.mongoConnector.db(), runner.rawCommand(commandDoc))
      .one[JsObject](ReadPreference.primaryPreferred)
      .map { response =>
        {
          response.as((JsPath \ "ok").read[Double]) match {
            case 1.0 =>
              for {
                oldTtl <- response.as((JsPath \ "expireAfterSeconds_old").readNullable[Double])
                newTtl <- response.as((JsPath \ "expireAfterSeconds_new").readNullable[Double])
              } yield Logger.warn(s"Change to TTL: old TTL $oldTtl, new TTL $newTtl")
              true
            case _ =>
              Logger.warn(s"Change to TTL failed. response: $response")
              false
          }
        }
      }
  }

  override def getHousekeepingStatus: Future[HousekeepingStatus] =
    collection.indexesManager.list().map { indexes =>
      val optTtlSecs = for {
        idx <- indexes.find(_.key.map(_._1).contains("housekeepingAt"))
        // Read the expiry from JSON (rather than BSON) so that we can control widening to Long
        // (from the more strongly typed BSON values which can be either Int32 or Int64)
        value <- Json.toJson(idx.options).as((JsPath \ "expireAfterSeconds").readNullable[Long])
      } yield value

      optTtlSecs match {
        case Some(`housekeepingOnTTLSecs`)  => HousekeepingStatus.On
        case Some(`housekeepingOffTTLSecs`) => HousekeepingStatus.Off
        case Some(other) =>
          Logger.warn(
            s"Cannot get housekeeping status: expireAfterSeconds is $other (neither on: $housekeepingOnTTLSecs nor off: $housekeepingOffTTLSecs)")
          HousekeepingStatus.Unknown
        case None =>
          Logger.warn(s"Cannot housekeeping status: expireAfterSeconds could not be determined")
          HousekeepingStatus.Unknown
      }
    }
}
