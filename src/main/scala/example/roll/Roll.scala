package example.roll

import scala.scalajs.js

import example.cp.Implicits._
import org.scalajs.dom.extensions._

import example.{cp, Game}
import org.scalajs.dom


object Roll{
  def draw(ctx: dom.CanvasRenderingContext2D, form: Form) = {
    val body = form.body
    ctx.save()
    ctx.lineWidth = 3
    ctx.strokeStyle = form.strokeStyle
    ctx.fillStyle = form.fillStyle
    ctx.translate(
      body.getPos().x,
      body.getPos().y
    )

    ctx.rotate(body.a)
    form.drawable.draw(ctx)
    ctx.restore()
  }
}

case class Roll(src: String, viewPort: () => cp.Vect) extends Game {

  implicit val space = new cp.Space()
  space.damping = 0.95
  space.gravity = (0, 400)

  val rock = Form.makePoly(
    points = Seq((1111500, 111300), (1111500, 111301), (1111501, 111300)),
    density = 1,
    static = false,
    friction = 0.6,
    elasticity = 0.6
  )

  val svgDoc = new dom.DOMParser().parseFromString(
    scala.js.bundle.apply(src).string,
    "text/xml"
  )

  val static =
    svgDoc.getElementById("Static")
          .children
          .flatMap(Form.processElement(_, static = true))

  val dynamic =
    svgDoc.getElementById("Dynamic")
          .children
          .flatMap(Form.processElement(_, static = false))

  val svg = svgDoc.getElementsByTagName("svg")(0).cast[dom.SVGSVGElement]

  def widest = new cp.Vect(
    svg.width,
    svg.height
  )

  val backgroundImg = {
    dom.extensions.Image.createBase64Svg(
      dom.btoa(
        s"<svg xmlns='http://www.w3.org/2000/svg' width='${widest.x}' height='${widest.y}'>" +
          new dom.XMLSerializer().serializeToString(svgDoc.getElementById("Background")) +
          "</svg>"
      )
    )
  }

  val clouds = new Clouds(widest, viewPort)

  val staticJoints =
    svgDoc.getElementById("Joints")
          .children
          .flatMap(Form.processJoint)

  val player = new Player(Form.processElement(svgDoc.getElementById("Player"), static = false)(space)(0))

  val goal = new Goal(Form.processElement(svgDoc.getElementById("Goal"), static = true)(space)(0))
  space.addCollisionHandler(1, 1, null, (arb: cp.Arbiter, space: cp.Space) => goal.hit(), null)

  val strokes = new Strokes(space)

  val lasers = new Lasers(
    player = player.form,
    laserElement = svgDoc.getElementById("Lasers"),
    query = space.segmentQueryFirst(_, _, ~Layers.Static, 0),
    kill = if (player.dead == 0.0) player.dead = 1.0
  )


  var camera: Camera = new Camera.Pan(
    viewPort,
    widest,
    checkpoints = List(
      (goal.p, 1),
      (widest / 2, math.min(viewPort().x / widest.x, viewPort().y / widest.y))
    ),
    finalCamera = new Camera.Follow(
      if (goal.won) goal.p
      else player.form.body.getPos() + player.form.body.getVel(),
      widest,
      viewPort,
      1
    )
  )

  def drawStatic(ctx: dom.CanvasRenderingContext2D, w: Int, h: Int) = {

  }

  def draw(ctx: dom.CanvasRenderingContext2D) = {
    ctx.fillStyle = "#82CAFF"
    ctx.fillRect(0, 0, viewPort().x, viewPort().y)
    strokes.drawStatic(ctx, viewPort().x, viewPort().y)
    camera.transform(ctx){ ctx =>
      ctx.lineCap = "round"
      ctx.lineJoin = "round"
      clouds.draw(ctx)
      ctx.drawImage(backgroundImg, 0, 0)

      for(form <- static ++ dynamic if form != null){
        Roll.draw(ctx, form)
      }

      lasers.draw(ctx)
      player.draw(ctx)
      goal.draw(ctx)

      staticJoints.foreach{ jform  =>
        ctx.save()
        ctx.fillStyle = jform.fillStyle
        ctx.strokeStyle = jform.strokeStyle
        ctx.translate(jform.joint.a.getPos().x, jform.joint.a.getPos().y)
        ctx.rotate(jform.joint.a.a)
        ctx.fillCircle(jform.joint.anchr1.x, jform.joint.anchr1.y, 5)
        ctx.restore()
      }

      strokes.draw(ctx)
    }
  }

  def update(keys: Set[Int],
             lines: Seq[(cp.Vect, cp.Vect)],
             touching: Boolean) = {

    camera.update(0.015, keys.toSet)
    clouds.update()
    lasers.update()

    player.update(keys)
    def screenToWorld(p: cp.Vect) = ((p - viewPort()/2) / camera.scale) + camera.pos
    strokes.update(lines.map(x => (screenToWorld(x._1), screenToWorld(x._2))), touching)

    space.step(1.0/60)
  }
}
