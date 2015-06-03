import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import com.deathhere.scala.examples.elevatorsystem.Controller
import com.deathhere.scala.examples.elevatorsystem.Controller.{Step, Pickup, StatusResponse, Status}
import com.typesafe.config.{ConfigFactory, Config}
import org.junit.runner.RunWith
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner
import scala.concurrent.Await
import scala.concurrent.duration._


@RunWith(classOf[JUnitRunner])
class ControllerTests extends TestKit(ActorSystem("ElevatorTestSystem")) with FunSuiteLike with ImplicitSender {

  implicit val timeout = Timeout(10 seconds)

  test("system picks with 10 floors and 2 elevator, picks up 5 people") {
    val controller = TestActorRef(new Controller(2, 10), "controller")

    controller ! Status
    expectMsg(Vector(StatusResponse(0,0,0), StatusResponse(1,0,0)))

    controller ! Pickup(0, 5)
    controller ! Pickup(0, 6)
    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,1,5), StatusResponse(1,1,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,2,5), StatusResponse(1,2,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,3,5), StatusResponse(1,3,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,4,5), StatusResponse(1,4,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,5,5), StatusResponse(1,5,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,5,5), StatusResponse(1,6,6)))

    controller ! Pickup(3, 6)
    controller ! Pickup(8, 2)
    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,4,3), StatusResponse(1,6,6)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,3,6), StatusResponse(1,7,8)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,4,6), StatusResponse(1,8,2)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,5,6), StatusResponse(1,7,2)))

    controller ! Pickup(6, 1)
    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,6,2)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,5,1)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,4,1)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,3,1)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,2,1)))

    controller ! Step
    controller ! Status
    expectMsg(Vector(StatusResponse(0,6,6), StatusResponse(1,1,1)))
  }

}
