package server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import jsonUtil.JsonSupport
import model.Car
import persistence.CarDb
import persistence.CarDb.{AddCar, CarAdded, FindAllCars, FindCarsById}
import server.CarShop.dataSetup
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object LowerLevelCarShopApi extends App with JsonSupport {
  implicit val system: ActorSystem = ActorSystem("CarShop")
  implicit val materializer: ActorMaterializer.type = ActorMaterializer
  implicit val timeOut: Timeout = Timeout(3 seconds)
  import system.dispatcher

  val dbActor = dataSetup(system)

  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/car"), _, _, _) =>
      val maybeInt = uri.query().get("id").map(_.toInt)
      maybeInt.fold {
        val maybeCars = (dbActor ? FindAllCars).mapTo[Seq[Car]]
        maybeCars.map(car =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              car.toJson.prettyPrint
            )
          )
        )
      } { id =>
        val maybeCars = (dbActor ? FindCarsById(id)).mapTo[Option[Car]]
        maybeCars.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(car) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                car.toJson.prettyPrint
              )
            )
        }
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/car"), _, entity, _) =>
      val eventualStrict = entity.toStrict(3 seconds)
      eventualStrict.flatMap { strict =>
        val carJsonString = strict.data.utf8String
        val carObj = carJsonString.parseJson.convertTo[Car]
        val maybeCar =
          (dbActor ? AddCar(carObj)).mapTo[CarAdded]
        maybeCar.map(_ => HttpResponse(StatusCodes.OK))
      }

    case httpRequest: HttpRequest =>
      httpRequest.discardEntityBytes()
      Future(HttpResponse(StatusCodes.NotFound))
  }

  Http().newServerAt("localhost", 8080).bind(requestHandler)
}

object CarShop {
  def dataSetup(system: ActorSystem): ActorRef = {
    val dbActor: ActorRef = system.actorOf(Props[CarDb])
    val cars = Seq(Car("Honda"), Car("Toyota"), Car("Volkswagen"))
    cars.foreach(dbActor ! AddCar(_))
    dbActor
  }
}
