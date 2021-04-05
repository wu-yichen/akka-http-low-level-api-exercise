package GuitarShop

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, RootJsonFormat, enrichAny, enrichString}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait JsonSupport extends DefaultJsonProtocol {
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat2(Guitar)
}

case class Guitar(make: String, quantity: Int = 0)

object GuitarMeta {

  case class AddGuitar(guitar: Guitar)

  case class GuitarCreated(id: Int)

  case class FindGuitarsById(id: Int)

  case object FindAllGuitars

}

class Shop extends Actor with ActorLogging {

  import GuitarMeta._

  val shopDb = mutable.Map.empty[Int, Guitar]
  var id = 0

  override def receive: Receive = {
    case AddGuitar(guitar) =>
      val latestQuantity = guitar.quantity + 1
      shopDb.put(id, guitar)
      log.info(s"Guitar $guitar has been added id = $id")
      sender() ! GuitarCreated(id)
      id += 1
    case FindAllGuitars => sender() ! shopDb.values.toSeq
    case FindGuitarsById(id) =>
      val maybeGuitar = shopDb.get(id)
      sender() ! maybeGuitar
  }

}

object GuitarShop extends App with JsonSupport {
  implicit val system: ActorSystem = ActorSystem("GuitarShop")
  implicit val materializer: ActorMaterializer.type = ActorMaterializer
  implicit val timeOut: Timeout = Timeout(3 seconds)

  import GuitarMeta._
  import system.dispatcher

  val shopActor = system.actorOf(Props[Shop])
  setup()

  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/guitar"), _, _, _) =>
      val maybeInt = uri.query().get("id").map(_.toInt)
      maybeInt.fold {
        val guitars = (shopActor ? FindAllGuitars).mapTo[Seq[Guitar]]
        guitars.map(guitar =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          )
        )
      } { id =>
        val guitars = (shopActor ? FindGuitarsById(id)).mapTo[Option[Guitar]]
        guitars.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/guitar"), _, entity, _) =>
      val eventualStrict = entity.toStrict(3 seconds)
      eventualStrict.flatMap { strict =>
        val guitarJsonString = strict.data.utf8String
        val guitarObj = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarFuture = (shopActor ? AddGuitar(guitarObj)).mapTo[GuitarCreated]
        guitarFuture.map(_ =>
          HttpResponse(StatusCodes.OK)
        )
      }

    case httpRequest: HttpRequest =>
      httpRequest.discardEntityBytes()
      Future(HttpResponse(StatusCodes.NotFound))
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  private def setup(): Unit = {
    val guitars = Seq(Guitar("guitarA"), Guitar("guitarB"), Guitar("guitarC"))
    guitars.foreach(guitar => shopActor ! AddGuitar(guitar))
  }
}
