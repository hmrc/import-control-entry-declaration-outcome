
# Import Control Entry Declaration Outcome

The Import Control Entry Declaration Outcome responsibilities:
- receive and persist outcomes/decisions, and make them available to authenticated users. Message Types:
   - Accepted Amendment \<CC304> / IE304
   - Rejected Amendment \<CC305> / IE305
   - Accepted Declaration \<CC328> / IE328
   - Rejected Declaration \<CC316> / IE316 

## Development Setup
- MongoDB instance
- Run locally: `sbt run` which runs on port `9815` by default
- Run with test end points: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

## Tests
- Run Unit Tests: `sbt test`
- Run Integration Tests: `sbt it:test`

## API

|Internal paths prefixed by `/import-control-entry-declaration-outcome` | Supported Methods | Type | Description |
| ----------------------------------------------------------| ----------------- | -----|----------- |
|```/```                                    |        GET        | External | Endpoint for users to list unacknowledged decisions. |
|```/:correlationId```                      |        GET        | External | Endpoint for users to fetch an unacknowledged decision based on correlation Id. |
|```/:correlationId```                      |        DELETE     | External | Endpoint for users to acknowledge an unacknowledged decision based on correlation Id. |
|```/outcome```                             |        POST       | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to save a decision to the database. |
|```/test-only/outcomes/:submissionId ```   |        GET        | Test | Endpoint to get decision XML by submission Id. |

## API Reference / Documentation 
For more information on external API endpoints see the RAML at [Developer Hub] or using the endpoint below
|Path                          | Supported Methods | Description |
| -----------------------------| ----------------- | ----------- |
|```/api/conf/:version/*file```|        GET        | /api/conf/1.0/application.raml |

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
