package org.anant

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.streams.processor.{ProcessorContext, Processor};
import java.util.Properties
import scala.collection.mutable.Map
import scala.io.Source
import scala.util.{Try, Success, Failure}
import scalaj.http._

class RecordProcessor(projectProps : Properties) extends Processor[String, GenericRecord] {
  /*
   * For each record, receive from kafka streams, deserialize from avro, and write to C* db
   * - implements kafka processor api
   */

  private var context : ProcessorContext = _;
  private var cassandraKeyspace : String = projectProps.getProperty("cassandra.keyspace")
  private var cassandraTable : String = projectProps.getProperty("cassandra.table")
  private var apiHost : String = projectProps.getProperty("api.host")
  private var topic : String = projectProps.getProperty("kafka.topic")
  private var debugMode : Boolean = projectProps.getProperty("debug-mode").toBoolean

  // @SuppressWarnings("unchecked")
  // TODO find out how to do this in scala
  def init(pContext : ProcessorContext) {
    // keep the processor context locally
    context = pContext;

    // set some configs that we will use every time we process a record

    println("--------------------------")
    println("hitting endpoint:" + apiHost)

    cassandraKeyspace = projectProps.getProperty("cassandra.keyspace")
    cassandraTable = projectProps.getProperty("cassandra.table")

    // only topic we'll handle with this processor
    topic = projectProps.getProperty("kafka.topic")
  }

  def onComplete : PartialFunction[Try[HttpResponse[String]], Unit] = {
    case Success(response : HttpResponse[String]) => print(response)
    case Failure(err) => print(err)
  }

  // takes two args, key and value. Our value is an Avro GenericRecord
  def process(dummyKey : String, avroData : GenericRecord) {

    // set options
    val options = Map(("debugMode", debugMode))

    // KStreams already deserialized Avro record, so just convert to json, which we can send over HTTP
    val jsonData : String = avroData.toString()

    context.topic() match {
      // matches topic we are handling, send to C* table
      case topic => { CassandraUtil.writeToDb(apiHost, jsonData, onComplete, options) }
      case _ => { print(context.topic()) }
    }

    context.forward(dummyKey, avroData);
  }

  def close() {
      // nothing to do
  }

}


