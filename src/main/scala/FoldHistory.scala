import java.nio.file.FileSystems

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.collection.{immutable, mutable}

case class UniqueGroup[T](n: Int) extends GraphStage[FlowShape[T, mutable.LinkedHashSet[T]]] {
  require(n > 0, "n must be greater than 0")

  val in = Inlet[T]("UniqueGroup.in")
  val out = Outlet[mutable.LinkedHashSet[T]]("UniqueGroup.out")
  override val shape: FlowShape[T, mutable.LinkedHashSet[T]] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    private val buf = {
      val b = mutable.LinkedHashSet.empty[T]
      b.sizeHint(n)
      b
    }
    var left = n

    override def onPush(): Unit = {
      if (left == n) buf.clear()

      val elem = grab(in)
      buf -= elem += elem
      left -= 1
      if (left == 0) {
        left = n
        push(out, buf.result())
      } else {
        pull(in)
      }
    }

    override def onPull(): Unit = {
      pull(in)
    }

    override def onUpstreamFinish(): Unit = {
      // This means the buf is filled with some elements but not enough (left < n) to group together.
      // Since the upstream has finished we have to push them to downstream though.
      if (left < n) {
        left = n
        push(out, buf)
      }
      completeStage()
    }

    setHandlers(in, out, this)
  }

}

object FoldHistory extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val dirName = System.getProperty("user.home")
  val fileName = "/.bash_4history"
  val fileSource = FileIO.fromPath(FileSystems.getDefault.getPath(dirName, fileName))
  val fileSink = FileIO.toPath(FileSystems.getDefault.getPath(dirName, fileName + "-"))

  val delim = ByteString("\n")
  val uniqueGroupFlow: Flow[ByteString, mutable.LinkedHashSet[ByteString], NotUsed] = Flow[ByteString].via(UniqueGroup(5000))
  fileSource
    .via(Framing.delimiter(delim, Int.MaxValue))
    .via(WorkerPool(uniqueGroupFlow, 2))
//    .grouped(5000).map(_.foldLeft(mutable.LinkedHashSet.empty[ByteString])((ls, l) => ls -= l += l))
//    .via(UniqueGroup(5000))
    .fold(mutable.LinkedHashSet.empty[ByteString])((acc, set) => set.foldLeft(acc)((acc, s) => acc -= s += s))
    .mapConcat(ls => new immutable.Iterable[ByteString]() {
      def iterator = ls.iterator
    })
    .map(_ ++ delim)
    .toMat(fileSink)(Keep.right).run()
    .onComplete { case _ => system.terminate() }
}

object WorkerPool {
  def apply[In, Out](worker: Flow[In, Out, Any], workerCount: Int): Graph[FlowShape[In, Out], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val balance = b.add(Balance[In](workerCount))
      val resultsMerge = b.add(Merge[Out](workerCount))

      for (i <- 0 until workerCount)
        balance.out(i) ~> worker/*.async*/ ~> resultsMerge.in(i)

      FlowShape(balance.in, resultsMerge.out)
    }
  }
}
