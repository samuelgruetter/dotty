package dotty.tools
package dotc
package util

import scala.collection.mutable.ArrayBuffer
import dotty.tools.io._
import annotation.tailrec
import java.util.regex.Pattern
import java.io.IOException
import Chars._
import ScriptSourceFile._
import Positions._

object ScriptSourceFile {
  private val headerPattern = Pattern.compile("""^(::)?!#.*(\r|\n|\r\n)""", Pattern.MULTILINE)
  private val headerStarts  = List("#!", "::#!")

  def apply(file: AbstractFile, content: Array[Char]) = {
    /** Length of the script header from the given content, if there is one.
     *  The header begins with "#!" or "::#!" and ends with a line starting
     *  with "!#" or "::!#".
     */
    val headerLength =
      if (headerStarts exists (content startsWith _)) {
        val matcher = headerPattern matcher content.mkString
        if (matcher.find) matcher.end
        else throw new IOException("script file does not close its header with !# or ::!#")
      } else 0
    new SourceFile(file, content drop headerLength) {
      override val underlying = new SourceFile(file, content)
    }
  }
}

case class SourceFile(file: AbstractFile, content: Array[Char]) {

  def this(_file: AbstractFile)                 = this(_file, _file.toCharArray)
  def this(sourceName: String, cs: Seq[Char])   = this(new VirtualFile(sourceName), cs.toArray)
  def this(file: AbstractFile, cs: Seq[Char])   = this(file, cs.toArray)

  /** Tab increment; can be overridden */
  def tabInc = 8

  override def equals(that : Any) = that match {
    case that : SourceFile => file.path == that.file.path && start == that.start
    case _ => false
  }
  override def hashCode = file.path.## + start.##

  def apply(idx: Int) = content.apply(idx)

  val length = content.length

  /** true for all source files except `NoSource` */
  def exists: Boolean = true

  /** The underlying source file */
  def underlying: SourceFile = this

  /** The start of this file in the underlying source file */
  def start = 0

  def atPos(pos: Position): SourcePosition =
    if (pos.exists) SourcePosition(underlying, pos)
    else NoSourcePosition

  def isSelfContained = underlying eq this

  /** Map a position to a position in the underlying source file.
   *  For regular source files, simply return the argument.
   */
  def positionInUltimateSource(position: SourcePosition): SourcePosition =
    SourcePosition(underlying, position.pos shift start)

  def isLineBreak(idx: Int) =
    if (idx >= length) false else {
      val ch = content(idx)
      // don't identify the CR in CR LF as a line break, since LF will do.
      if (ch == CR) (idx + 1 == length) || (content(idx + 1) != LF)
      else isLineBreakChar(ch)
    }

  def calculateLineIndices(cs: Array[Char]) = {
    val buf = new ArrayBuffer[Int]
    buf += 0
    for (i <- 0 until cs.length) if (isLineBreak(i)) buf += i + 1
    buf += cs.length // sentinel, so that findLine below works smoother
    buf.toArray
  }
  private lazy val lineIndices: Array[Int] = calculateLineIndices(content)

  /** Map line to offset of first character in line */
  def lineToOffset(index : Int): Int = lineIndices(index)

  /** A cache to speed up offsetToLine searches to similar lines */
  private var lastLine = 0

  /** Convert offset to line in this source file
   *  Lines are numbered from 0
   */
  def offsetToLine(offset: Int): Int = {
    val lines = lineIndices
    def findLine(lo: Int, hi: Int, mid: Int): Int =
      if (offset < lines(mid)) findLine(lo, mid - 1, (lo + mid - 1) / 2)
      else if (offset >= lines(mid + 1)) findLine(mid + 1, hi, (mid + 1 + hi) / 2)
      else mid
    lastLine = findLine(0, lines.length, lastLine)
    lastLine
  }

  def startOfLine(offset: Int): Int = lineToOffset(offsetToLine(offset))

  def nextLine(offset: Int): Int =
    lineToOffset(offsetToLine(offset) + 1 min lineIndices.length - 1)

  def lineContents(offset: Int): String =
    content.slice(startOfLine(offset), nextLine(offset)).mkString

  def column(offset: Int): Int = {
    var idx = startOfLine(offset)
    var col = 0
    while (idx != offset) {
      col += (if (content(idx) == '\t') tabInc - col % tabInc else 1)
      idx += 1
    }
    col + 1
  }

  override def toString = file.toString
}

object NoSource extends SourceFile("<no source>", Nil) {
  override def exists = false
}

