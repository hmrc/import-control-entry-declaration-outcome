/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import org.bson.BsonValue
import org.mongodb.scala._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.bson.conversions.Bson
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, OutcomeMetadata, OutcomeReceived, OutcomeXml, EntryObjectId}
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
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
  def listOutcomes(eori: String, optionalCSPUserId: Option[String] = None): Future[List[OutcomeMetadata]]
  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]
  def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean]
  def housekeep(now: Instant): Future[Int]
}

@Singleton
class OutcomeRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: MongoComponent,
  ec: ExecutionContext,
  mat: Materializer
) extends PlayMongoRepository[OutcomePersisted](
  collectionName = "outcome",
  mongoComponent = mongo,
  domainFormat = OutcomePersisted.format,
  indexes = Seq(IndexModel(ascending("submissionId"),
                           IndexOptions()
                            .name("submissionIdIndex")
                            .unique(true)),
                IndexModel(ascending("housekeepingAt"),
                           IndexOptions()
                            .name("housekeepingIndex")),
                IndexModel(ascending("eori",
                                     "acknowledged",
                                     "receivedDateTime",
                                     "correlationId",
                                     "movementReferenceNumber"),
                           IndexOptions()
                            .name("listIndex")
                            .unique(false)),
                IndexModel(ascending("eori", "correlationId"),
                           IndexOptions()
                            .name("eoriPlusCorrelationIdIndex")
                            .unique(true))
                ),
  extraCodecs = Seq(Codecs.playFormatCodec(MongoFormats.objectIdFormat)),
  replaceIndexes = true)
    with OutcomeRepo with RepositoryFns {

  private val mongoErrorCodeForDuplicate: Int = 11000

  //
  // Test FNs
  //
  def find(submissionId: String): Future[Option[OutcomePersisted]] =
    collection
      .find(equal("submissionId", submissionId))
      .headOption()

  def find(eori: String, correlationId: String): Future[Option[OutcomePersisted]] =
    collection
      .find(and(equal("eori", eori), equal("correlationId", correlationId)))
      .headOption()

  def save(outcome: OutcomeReceived)(implicit lc: LoggingContext): Future[Option[SaveError]] =
    Mdc.preservingMdc(
      collection
        .insertOne(OutcomePersisted.from(outcome, appConfig.defaultTtl))
        .toFutureOption()
    )
    .map(_ => None)
    .recover {
      case ex: MongoWriteException if ex.getCode == mongoErrorCodeForDuplicate =>
        ContextLogger.error(s"Duplicate entry declaration outcome", ex)
        Some(SaveError.Duplicate)
      case ex =>
        ContextLogger.error(s"Unable to save entry declaration outcome", ex)
        Some(SaveError.ServerError)
    }

  //Test only -> so can retrieve even if acknowledged
  def lookupOutcomeXml(submissionId: String): Future[Option[OutcomeXml]] = {
    Mdc.preservingMdc(
      collection
        .find[BsonValue](equal("submissionId", submissionId))
        .projection(fields(include("outcomeXml"), excludeId()))
        .headOption()
        .map{
          _.map(xml => Codecs.fromBson[OutcomeXml](xml))
        }
    )
  }

  def lookupOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]] =
    Mdc.preservingMdc(
      collection
        .find(and(equal("eori", eori),
                  equal("correlationId", correlationId),
                  equal("acknowledged", false)
              )
        )
        .headOption()
    )
    .map(_.map(_.toOutcomeReceived))

  override def lookupFullOutcome(eori: String, correlationId: String): Future[Option[FullOutcome]] =
    Mdc.preservingMdc(
      collection
        .find(and(equal("eori", eori), equal("correlationId", correlationId)))
        .headOption()
    )
    .map(_.map(_.toFullOutcome))

  def acknowledgeOutcome(eori: String, correlationId: String, time: Instant)(implicit lc: LoggingContext): Future[Option[OutcomeReceived]] =
    Mdc.preservingMdc(
      collection
        .findOneAndUpdate(and(equal("eori", eori), equal("correlationId", correlationId), equal("acknowledged", false)),
                          combine(set("acknowledged", true), set("housekeepingAt", time)),
                          FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER))
        .toFutureOption()
    )
    .map(_.map(_.toOutcomeReceived))

  def listOutcomes(eori: String, optionalCSPUserId: Option[String] = None): Future[List[OutcomeMetadata]] = {
    val findEoriAndNotAcknowledged: Bson = and(equal("eori", eori), equal("acknowledged", false))

    val findCriteria =
      optionalCSPUserId match {
        case Some(cspUserId) =>
          // match only correlationIds containing the CSPUserId provided at the end
          and(findEoriAndNotAcknowledged, regex("correlationId", cspUserId + "$"))
        case None =>
          // if none is provided, do not filter for a specific software (as user is logged-in via GGW)
          findEoriAndNotAcknowledged
      }

    Mdc.preservingMdc(
      collection
        .find[BsonValue](findCriteria)
        .projection(include("correlationId", "movementReferenceNumber"))
        .sort(ascending("receivedDateTime"))
        .limit(appConfig.listOutcomesLimit)
        .collect()
        .toFutureOption()
    )
    .map{
      case Some(results) => results.map(Codecs.fromBson[OutcomeMetadata](_)).toList
      case _ => Nil
    }
  }

  override def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, equal("submissionId", submissionId))

  override def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, and(equal("eori", eori), equal("correlationId", correlationId)))

  private def setHousekeepingAt(time: Instant, query: Bson): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(query, set("housekeepingAt", time))
          .toFutureOption()
      )
      .map(_.map(_.getMatchedCount > 0).getOrElse(false))

  override def housekeep(now: Instant): Future[Int] =
    Mdc.preservingMdc(
      Source.fromPublisher(
        collection
          .find[BsonValue](lte("housekeepingAt", now))
          .projection(fields(include("_id")))
          .sort(ascending("housekeepingAt"))
          .limit(appConfig.housekeepingRunLimit)
      )
      .batch(appConfig.housekeepingBatchSize, List(_)) { (deletions, element) =>
        element :: deletions
      }
      .mapAsync(1) { deletions =>
        collection.bulkWrite(deletions.map(oid => DeleteManyModel(equal("_id", Codecs.fromBson[EntryObjectId](oid)._id))))
          .toFutureOption()
          .map(_.map(_.getDeletedCount).getOrElse(0))
          .recover{
            case e => 0
          }
      }
      .runFold(0)(_ + _))
}
