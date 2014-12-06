package edu.arizona.sista.matcher

import edu.arizona.sista.processors.Document

object ThompsonVM {
  type Sub = Map[String, (Int, Int)]

  private case class Thread(inst: Inst) {
    var sub: Sub = _
  }

  private object Thread {
    def apply(inst: Inst, sub: Sub): Thread = {
      val t = Thread(inst)
      t.sub = sub
      t
    }
  }

  def evaluate(start: Inst, tok: Int, sent: Int, doc: Document): Option[Sub] = {
    def mkThreads(tok: Int, inst: Inst, sub: Sub): Seq[Thread] = inst match {
      case i: Jump => mkThreads(tok, i.next, sub)
      case i: Split => mkThreads(tok, i.lhs, sub) ++ mkThreads(tok, i.rhs, sub)
      case i: SaveStart => mkThreads(tok, i.next, sub + (i.name -> (tok, -1)))
      case i: SaveEnd => mkThreads(tok, i.next, sub + (i.name -> (sub(i.name)._1, tok)))
      case _ => Seq(Thread(inst, sub))
    }

    def stepThreads(tok: Int, threads: Seq[Thread]): Seq[Thread] =
      threads.flatMap(t => t.inst match {
        case i: Match if tok < doc.sentences(sent).size && i.c.matches(tok, sent, doc) =>
          mkThreads(tok + 1, i.next, t.sub)
        case _ => Nil
      }).distinct

    def handleMatch(tok: Int, threads: Seq[Thread]): (Seq[Thread], Option[Sub]) =
      threads.find(_.inst == Done) match {
        case None => (threads, None)
        case Some(t) => (threads.takeWhile(_ != t), Some(t.sub))
      }

    @annotation.tailrec
    def loop(i: Int, threads: Seq[Thread], result: Option[Sub]): Option[Sub] = {
      if (threads.isEmpty) result
      else {
        val (ts, r) = handleMatch(i, threads)
        loop(i + 1, stepThreads(i, ts), r)
      }
    }

    loop(tok, mkThreads(tok, start, Map.empty), None)
  }
}

sealed abstract class Inst {
  var next: Inst = null
  def dup: Inst
}

case class Split(lhs: Inst, rhs: Inst) extends Inst {
  def dup: Inst = Split(lhs.dup, rhs.dup)
}

case class Match(c: TokenConstraint) extends Inst {
  def dup: Inst = {
    val inst = copy()
    if (inst.next != null) inst.next = next.dup
    inst
  }
}

case class Jump() extends Inst {
  def dup: Inst = {
    val inst = copy()
    if (inst.next != null) inst.next = next.dup
    inst
  }
}

case class SaveStart(name: String) extends Inst {
  def dup: Inst = {
    val inst = copy()
    if (inst.next != null) inst.next = next.dup
    inst
  }
}

case class SaveEnd(name: String) extends Inst {
  def dup: Inst = {
    val inst = copy()
    if (inst.next != null) inst.next = next.dup
    inst
  }
}

case object Done extends Inst {
  def dup: Inst = this
}
