/*
 *
 *  Copyright (c) 2021. Mark Grechanik and Lone Star Consulting, Inc. All rights reserved.
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under
 *   the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *   either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 *
 */
import Generation.{LogMsgSimulator, RandomStringGenerator}
import HelperUtils.{CreateLogger, Parameters}
import org.slf4j.Logger
import Generation.RSGStateMachine.*
import Generation.*
import HelperUtils.Parameters.*
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client

import scala.jdk.CollectionConverters.*
import scala.concurrent.{Await, Future, duration}
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Success, Try}

object GenerateLogData:

  private val logger = CreateLogger(classOf[GenerateLogData.type])
  private val localFileName = "log/input.log"


  private val bucket = Parameters.awsBucket
  private val key = Parameters.awsKey

  private val awsAccessKey = Parameters.awsAccessKey
  private val awsSecretKey = Parameters.awsSecretKey

  private val AWSCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey)
  private val awsClient = new AmazonS3Client(AWSCredentials)

//this is the main starting point for the log generator
  @main def runLogGenerator(): Unit =

    logger.info("Log data generator started...")
    val init = unit()

    val logFuture = Future {
      LogMsgSimulator(init(RandomStringGenerator((Parameters.minStringLength, Parameters.maxStringLength), Parameters.randomSeed)), Parameters.maxCount)
    }
    Try(Await.result(logFuture, Parameters.runDurationInMinutes)) match {
      case Success(_) => logger.info(s"Log data generation has completed after generating ${Parameters.maxCount} records.")
      case Failure(_) => logger.info(s"Log data generation has completed within the allocated time, ${Parameters.runDurationInMinutes}")
    }

    writeFileOnS3()

  end runLogGenerator


  private def getFileAsString: String =

    logger.info(s"Attempt to read file $localFileName")
    val source = Source.fromFile(localFileName)

    if (source.isEmpty) {
      source.close()
      logger.error(s"Content of file $localFileName is empty!")
      throw new RuntimeException(s"Content of file $localFileName is empty!")
    }

    val outputLines = source.getLines().toArray.reduce((l1, l2) => l1 + "\n" + l2)

    source.close()

    logger.info(s"Content of file $localFileName read correctly")

    outputLines

  end getFileAsString


  private def writeFileOnS3(): Unit =

    val file = getFileAsString

    logger.info(s"Writing content of file $localFileName inside the S3 file $key in bucket $bucket")
    if (awsClient.doesObjectExist(bucket, key)) {
      awsClient.deleteObject(bucket, key)
    }
    awsClient.putObject(bucket, key, file)

    logger.info(s"Writing performed correctly")

  end writeFileOnS3

end GenerateLogData


