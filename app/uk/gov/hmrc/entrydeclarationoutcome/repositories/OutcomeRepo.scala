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

import akka.stream.Materializer
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, OutcomeMetadata, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait OutcomeRepo {
  def save(outcome: OutcomeReceived)(implicit lc: LoggingContext): Future[Option[SaveError]]

  def lookupOutcomeXml(submissionId: String): Future[Option[OutcomeXml]]

  def lookupOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]]

  def lookupFullOutcome(eori: String, correlationId: String): Future[Option[FullOutcome]]

  /**
    * @return the acknowledged outcome
    */
  def acknowledgeOutcome(eori: String, correlationId: String, time: Instant)(
    implicit lc: LoggingContext): Future[Option[OutcomeReceived]]

  def listOutcomes(eori: String): Future[List[OutcomeMetadata]]

  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]

  def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean]

  def housekeep(now: Instant): Future[Int]
}

@Singleton
class OutcomeRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext,
  mat: Materializer
) extends ReactiveRepository[OutcomePersisted, BSONObjectID](
      "outcome",
      mongo.mongoConnector.db,
      OutcomePersisted.format,
      ReactiveMongoFormats.objectIdFormats)
    with OutcomeRepo {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("submissionId", Ascending)), name = Some("submissionIdIndex"), unique = true),
    Index(Seq("housekeepingAt" -> Ascending), name = Some("housekeepingIndex")),
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
    val outcomePersisted = OutcomePersisted.from(outcome, appConfig.defaultTtl)

    Mdc
      .preservingMdc(insert(outcomePersisted))
      .map(_ => None)
      .recover {
        case e: DatabaseException =>
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
    Mdc
      .preservingMdc(
        collection
          .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("outcomeXml" -> 1)))
          .one[OutcomeXml])

  def lookupOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]] =
    Mdc
      .preservingMdc(
        collection
          .find(
            Json.obj("eori" -> eori, "correlationId" -> correlationId, "acknowledged" -> false),
            Option.empty[JsObject])
          .one[OutcomePersisted])
      .map(_.map(_.toOutcomeReceived))

  override def lookupFullOutcome(eori: String, correlationId: String): Future[Option[FullOutcome]] =
    Mdc
      .preservingMdc(
        collection
          .find(Json.obj("eori" -> eori, "correlationId" -> correlationId), Option.empty[JsObject])
          .one[OutcomePersisted])
      .map(_.map(_.toFullOutcome))

  def acknowledgeOutcome(eori: String, correlationId: String, time: Instant)(
    implicit lc: LoggingContext): Future[Option[OutcomeReceived]] =
    Mdc
      .preservingMdc(
        findAndUpdate(
          query          = Json.obj("eori" -> eori, "correlationId" -> correlationId, "acknowledged" -> false),
          update         = Json.obj("$set" -> Json.obj("acknowledged" -> true, "housekeepingAt" -> PersistableDateTime(time))),
          fetchNewObject = true
        ))
      .map(result => result.result[OutcomePersisted].map(_.toOutcomeReceived))

  def listOutcomes(eori: String): Future[List[OutcomeMetadata]] =
    Mdc.preservingMdc(
      collection
        .find(
          Json.obj("eori" -> eori, "acknowledged" -> false),
          Some(Json.obj("correlationId" -> 1, "movementReferenceNumber" -> 1)))
        .sort(Json.obj("receivedDateTime" -> 1))
        .cursor[OutcomeMetadata]()
        .collect[List](maxDocs = appConfig.listOutcomesLimit, err = Cursor.FailOnError[List[OutcomeMetadata]]()))

  override def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, Json.obj("submissionId" -> submissionId))

  override def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, Json.obj("eori" -> eori, "correlationId" -> correlationId))

  private def setHousekeepingAt(time: Instant, query: JsObject): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .update(ordered = false, WriteConcern.Default)
          .one(query, Json.obj("$set" -> Json.obj("housekeepingAt" -> PersistableDateTime(time)))))
      .map(result => result.n == 1)

  override def housekeep(now: Instant): Future[Int] = {
    val deleteBuilder = collection.delete(ordered = false)

    Mdc
      .preservingMdc(
        collection
          .find(
            selector   = Json.obj("housekeepingAt" -> Json.obj("$lte" -> PersistableDateTime(now))),
            projection = Some(Json.obj("_id" -> 1))
          )
          .sort(Json.obj("housekeepingAt" -> 1))
          .cursor[JsObject]()
          .documentSource(maxDocs = appConfig.housekeepingRunLimit)
          .mapAsync(1) { idDoc =>
            deleteBuilder.element(q = idDoc, limit = Some(1), collation = None)
          }
          .batch(appConfig.housekeepingBatchSize, List(_)) { (deletions, element) =>
            element :: deletions
          }
          .mapAsync(1) { deletions =>
            collection
              .delete()
              .many(deletions)
              .map(_.n)
          }
          .runFold(0)(_ + _))
  }
}
