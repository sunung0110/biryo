package net.kinetc.biryo

import scala.collection.Seq
import HTMLRenderer._

object NamuAST {
  type NamuMap = PartialFunction[NamuMark, NamuMark]
  // s"\"" -> s"$q"  (Build Error???)
  val q = '"'
  def toQ(s: String) = '\"' + s + '\"'

  trait NamuMark {
    def mkString: String = ""

    /**
      * Child-First Traversing
      */
    def cfs(f: NamuMark => Unit): Unit = f(this)

    /**
      * Node-First Traversing
      */
    def nfs(f: NamuMark => Unit): Unit = f(this)

    /**
      * Child-First Node Mapping
      * @param f: PartialFunction, don't need to apply it to child
      * @return default: this, or f.apply(this)
      */
    def cfsMap(f: NamuMap): NamuMark = if (f.isDefinedAt(this)) f(this) else this

    /**
      * Node-First Node Mapping
      * @param f PartialFunction, You should apply it to child when `this` is Paragraph or DocLink
      * @return default: this, or f.apply(this)
      */
    def nfsMap(f: NamuMap): NamuMark = cfsMap(f)
  }

  /**
    * A Trait which has a single NamuMark value
    */
  trait HasNamu extends NamuMark {
    val value: NamuMark
    def constructor(nm: NamuMark): NamuMark

    override def cfs(f: (NamuMark) => Unit) = { value.cfs(f); f(this) }
    override def nfs(f: (NamuMark) => Unit) = { f(this); value.nfs(f) }

    override def cfsMap(f: NamuMap): NamuMark = {
      val childMap = value.cfsMap(f)
      val newThis = constructor(childMap)
      if (f.isDefinedAt(newThis)) f(newThis) else newThis
    }
    override def nfsMap(f: NamuMap): NamuMark = {
      if (f.isDefinedAt(this)) {
        val newThis = f(this)
        newThis match {
          case t: HasNamu => t.constructor(t.value.nfsMap(f))
          case _ => newThis
        }
      } else {
        constructor(value.nfsMap(f))
      }
    }
  }

  /**
    * A Class that has a derived type or sequence of NamuMark
    * @param valueSeq Traversable NamuMark Values
    * @tparam K Child of NamuMark
    * @tparam T can be Traversable implicitly (e.g. Option, Seq, ...)
    */
  abstract class HasNamuSeq[+K <: NamuMark, T](valueSeq: T)(implicit e: T => Traversable[K])
    extends NamuMark {
    def constructor(nm: T): NamuMark

    override def mkString =
      valueSeq.foldLeft(new StringBuilder)((sb, nm) => sb.append(nm.mkString)).toString
    override def cfs(f: (NamuMark) => Unit) = { valueSeq.foreach(_.cfs(f)); f(this) }
    override def nfs(f: (NamuMark) => Unit) = { f(this); valueSeq.foreach(_.nfs(f)) }
    override def cfsMap(f: NamuMap) = {
      val childMap = valueSeq.map(_.cfsMap(f)).asInstanceOf[T]
      val newThis = constructor(childMap)
      if (f.isDefinedAt(newThis)) f(newThis) else newThis
    }
    override def nfsMap(f: NamuMap): NamuMark =
      if (f.isDefinedAt(this)) f(this)
      else constructor(valueSeq.map(_.nfsMap(f)).asInstanceOf[T])
  }

  trait HasHref {
    val href: NamuHref
    def hrefConstructor(href: NamuHref): NamuMark
    def hrefMap(f: NamuHref => NamuHref): NamuMark = hrefConstructor(f(href))
  }

  ////// ------ Paragraph ------- ///////

  /**
    * List of NamuMark Objects (`Seq[NamuMark]` Wrapper)
    * @param valueSeq a sequence of NamuMark Objects
    */
  case class Paragraph(valueSeq: Seq[NamuMark]) extends HasNamuSeq[NamuMark, Seq[NamuMark]](valueSeq) {
    def constructor(nm: Seq[NamuMark]) = Paragraph(nm)
  }

  case class ParagraphBuilder(markList: Seq[NamuMark], sb: StringBuilder) extends NamuMark

  ////// ------ Indent & Lists ------ //////

  case class ListObj(value: NamuMark, listType: ListType, indentSize: Int) extends HasNamu {
    def constructor(nm: NamuMark) = ListObj(nm, listType, indentSize)
  }

  sealed trait ListType
  // * star -> <ul> ~~ </ul>
  case object Type_star extends ListType
  // 1.#42 -> <ol type="1" start="42"> ~~ </ol>
  case class Type_1(offset: Int = 1) extends ListType
  // i.#42 -> <ol type="i" start="42"> ~~ </ol>
  case class Type_i(offset: Int = 1) extends ListType
  // I.#42 -> <ol type="I" start="42"> ~~ </ol>
  case class Type_I(offset: Int = 1) extends ListType
  // a.#42 -> <ol type="a" start="42"> ~~ </ol>
  case class Type_a(offset: Int = 1) extends ListType
  // A.#42 -> <ol type="A" start="42"> ~~ </ol>
  case class Type_A(offset: Int = 1) extends ListType

  ////// ------ Table ------ //////

  // TODO: Can I restrict a type of valueSeq to Seq[TR]??
  case class Table(valueSeq: Seq[TR], styles: Seq[TableStyle]) extends HasNamuSeq[TR, Seq[TR]](valueSeq) {
    def constructor(nm: Seq[TR]) = Table(nm, styles)
  }
  case class TR(valueSeq: Seq[TD], styles: Seq[TableStyle]) extends HasNamuSeq[TD, Seq[TD]](valueSeq) {
    def constructor(nm: Seq[TD]) = TR(nm, styles)
  }
  case class TD(value: NamuMark, styles: Seq[TableStyle]) extends HasNamu {
    def constructor(nm: NamuMark) = TD(nm, styles)
  }


  ////// ------ Table Styles ------ //////

  sealed trait TableAlign
  case object AlignLeftTop extends TableAlign
  case object AlignCenter extends TableAlign
  case object AlignRightBottom extends TableAlign

  sealed trait TableStyle
  sealed trait TableAttr extends TableStyle {
    def attr: String
  }
  sealed trait TableCSS extends TableStyle {
    def style: String
  }
  case class BgColor(value: String, forTable: Boolean) extends TableCSS {
    def style: String = s"background-color:$value;"
  }
  case class RowBgColor(value: String) extends TableCSS {
    def style: String = s"background-color:$value;"
  }
  case class BorderColor(value: String) extends TableCSS {
    def style: String = s"border:2px solid $value;"
  }
  case class Width(value: String, forTable: Boolean) extends TableCSS {
    def style: String = s"width:$value;"
  }
  case class Height(value: String, forTable: Boolean) extends TableCSS {
    def style: String = s"height:$value;"
  }
  case class Align(align: TableAlign, forTable: Boolean) extends TableCSS {
    def style: String =
      if (forTable) {
        align match {
          case AlignCenter => "margin:auto;"
          case AlignRightBottom => "margin-right:0px;"
          case AlignLeftTop => "margin-left:0px;" // default
        }
      } else {
        align match {
          case AlignCenter => "text-align:center;"
          case AlignRightBottom => "text-align:right;"
          case AlignLeftTop => "text-align:left;" // default
        }
      }
  }
  case class RowSpan(value: Int, align: TableAlign) extends TableCSS with TableAttr {
    def style: String = align match {
      case AlignCenter => "" // default
      case AlignRightBottom => "vertical-align:bottom;"
      case AlignLeftTop => "vertical-align:top;"
    }
    def attr: String = s"rowspan=${toQ(value.toString)}"
  }
  case class ColSpan(value: Int) extends TableAttr {
    def attr: String = s"colspan=${toQ(value.toString)}"
  }


  ////// ------- BlockQuote ------ //////

  case class BlockQuote(value: NamuMark) extends HasNamu {
    override def mkString = s"<blockquote><div ${c(indentClass)}>${value.mkString}</div></blockquote>"
    def constructor(nm: NamuMark) = BlockQuote(nm)
  }

  ////// ------ Single Bracket FootNote / Macros ------ //////

  case class FootNote(value: NamuMark, noteStr: Option[String]) extends HasNamu {
    override def mkString = noteStr match {
      case Some(s) => s"<a name=${toQ(s"r$s")}></a><a href=${toQ(s"entry://#$s")}>[$s]</a>"
      case None => s"<a name=${toQ("rWTF")}></a><a href=${toQ("entry://#WTF")}>[*]</a>"
    }
    def constructor(nm: NamuMark) = FootNote(nm, noteStr)
  }

  // TODO: Calculate This!
  case class AgeMacro(date: String) extends NamuMark {
    override def mkString = s"(${date}로부터 나이)"
  }
  case object DateMacro extends NamuMark {
    // do not calculate it for MDict
    override def mkString = "[현재 시간]"
  }
  // TODO: Render This From HTMLRenderer
  case object FootNoteList extends NamuMark {
    override def mkString = "[각주]"
  }

  case object TableOfContents extends NamuMark {
    override def mkString = "[목차]"
  }
  // TODO: Should we render this??
  case class Include(rawHref: String, args: Map[String, String]) extends NamuMark {
    override def mkString = {
      if (args.isEmpty) {
        s"[include(<a href=${toQ(s"entry://$rawHref")}>$rawHref</a>)]"
      } else {
        val argString = args.mkString(", ").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        s"[include(<a href=${toQ(s"entry://$rawHref")}>$rawHref</a>), args:$argString)]"
      }
    }
  }
  case class Anchor(anchor: String) extends NamuMark {
    override def mkString = s"<a name=${toQ(anchor)}></a>"
  }

  case class YoutubeLink(id: String, args: Map[String, String]) extends NamuMark {
    // Fallback to Link (For MDict)
    override def mkString = s"<a href=${toQ(s"entry://https://www.youtube.com/watch?v=$id")}>[유튜브 링크]</a>"
  }

  ////// ------ Double Bracket Links ------ //////

  // [[파일:$href|$htmlOption]]
  case class FileLink(href: String, htmlOption: Map[String, String]) extends NamuMark {
    // Fallback to Link (for Mdict)
    override def mkString = s"<a href=${toQ(s"entry://$href")}>[파일:$href]</a>"
  }
  // [[분류:$docType]]
  case class DocType(docType: String) extends NamuMark {
    override def mkString = s"<div ${c(docTypeClass)}>" +
      s"분류: <a href=${toQ(s"entry://분류:$docType")}>$docType</a></div>"
  }

  // [[$href|$alias]] -> href will be changed to NormalHref after the postprocessing
  case class DocLink(href: NamuHref, valueSeq: Option[NamuMark])
    extends HasNamuSeq[NamuMark, Option[NamuMark]](valueSeq) with HasHref {
    override def mkString = valueSeq match {
      case Some(nm) => s"<a href=${toQ(href.value)}>${nm.mkString}</a>"
      case None => s"<a href=${toQ(href.value)}>${href.value}</a>"
    }

    def constructor(nm: Option[NamuMark]) = DocLink(href, nm)
    def hrefConstructor(href: NamuHref): NamuMark = DocLink(href, valueSeq)
  }

  ////// ------ Curly Brace Blocks ------ //////

  // {{{#!syntax $language $value}}}
  case class SyntaxBlock(language: String, value: String) extends NamuMark {
    override def mkString = s"<pre><code>$value</code></pre>"
  }
  // {{{$!wiki style="$style" $value}}}
  case class WikiBlock(style: String, value: NamuMark) extends NamuMark {
    override def mkString = s"<div style=${toQ(style)}>${value.mkString}</div>"
  }
  // {{|$value|}}
  case class WordBox(value: NamuMark) extends NamuMark with HasNamu {
    override def mkString = s"<table ${c(wordBoxClass)}><tbody><tr><td><p>${value.mkString}</p></td></tr></tbody></table>"
    def constructor(nm: NamuMark) = WordBox(nm)
  }
  case class SizeBlock(value: NamuMark, size: Int) extends HasNamu {
    override def mkString = s"<font size=${toQ(s"+$size")}>${value.mkString}</font>"
    def constructor(nm: NamuMark) = SizeBlock(nm, size)
  }
  case class ColorBlock(value: NamuMark, color: String) extends HasNamu {
    override def mkString = s"<font color=${toQ(color)}>${value.mkString}</font>"
    def constructor(nm: NamuMark) = ColorBlock(nm, color)
  }

  ////// ------ One-Liners ------- //////

  // ##Comment -> Comment("Comment")
  case class Comment(value: String) extends NamuMark

  // === Heading === -> RawHeadings(RawString("Heading"), 3)
  case class RawHeadings(value: NamuMark, size: Int) extends HasNamu {
    override def mkString = s"<h$size>${value.mkString}</h$size><hr>"
    def constructor(nm: NamuMark) = RawHeadings(nm, size)
  }

  // Post Process Only AST Node
  case class Headings(value: NamuMark, no: Seq[Int]) extends HasNamu {
    override def mkString = {
      val hsize = if (no.length <= 5) no.length + 1 else 6
      val hno = no.mkString(".")
      s"<h$hsize><a name=${toQ(s"s-$hno")}><a href=$q#headList$q>$hno. </a></a>${value.mkString}</h$hsize>"
    }
    def constructor(nm: NamuMark) = Headings(nm, no)
  }

  ////// ------ Span Marks ------ //////

  sealed trait SpanMark
  case class Strike(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<del>${value.mkString}</del>"
    def constructor(nm: NamuMark) = Strike(nm)
  }
  case class Sup(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<sup>${value.mkString}</sup>"
    def constructor(nm: NamuMark) = Sup(nm)
  }
  case class Sub(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<sub>${value.mkString}</sub>"
    def constructor(nm: NamuMark) = Sub(nm)
  }
  case class Underline(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<u>${value.mkString}</u>"
    def constructor(nm: NamuMark) = Underline(nm)
  }
  case class Bold(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<b>${value.mkString}</b>"
    def constructor(nm: NamuMark) = Bold(nm)
  }
  case class Italic(value: NamuMark) extends HasNamu with SpanMark {
    override def mkString = s"<i>${value.mkString}</i>"
    def constructor(nm: NamuMark) = Italic(nm)
  }

  case class Redirect(value: String) extends NamuMark {
    override def mkString = s"<a href=${toQ(s"entry://$value")}>리다이렉트:$value</a>"
  }

  ////// ------ Basic Blocks ------- //////

  // HTML Unescaped String {{{#!html ... }}}
  case class HTMLString(value: String) extends NamuMark {
    override def mkString = value
  }
  // HTML Escaped Normal String
  case class RawString(value: String) extends NamuMark {
    override def mkString = value
  }
  // Markup Ignored String {{{ ... }}}
  case class InlineString(value: String, isMultiLine: Boolean) extends NamuMark {
    override def mkString =
      if (isMultiLine) s"<pre><code>$value</code></pre>" else s"<code>$value</code>"
  }
  // [br] -> BR
  case object BR extends NamuMark {
    override def mkString = "<br>"
  }
  // ---- ~ ---------- (4 to 10 times)
  case object HR extends NamuMark {
    override def mkString = "<hr>"
  }

  ////// ------ href Trait ------ //////

  sealed trait NamuHref {
    val value: String
  }
  // [[value]] => NormalHref("value")
  case class NormalHref(value: String) extends NamuHref
  // [[value#s-0.1.2]] => ParaHref("value", Seq[Int](0, 1, 2))
  case class ParaHref(value: String, paraNo: Seq[Int]) extends NamuHref
  // [[value#anchor]] => AnchorHref("value", "anchor")
  case class AnchorHref(value: String, anchor: String) extends NamuHref
  // [[http://example.com]] => ExternalHref("http://example.com")
  case class ExternalHref(value: String) extends NamuHref
  // [[#1.4.1]] => SelfParaHref(Seq[Int](1,4,1))
  case class SelfParaHref(paraNo: Seq[Int]) extends NamuHref {
    val value: String = "#s-" + paraNo.mkString(".")
  }
  // [[#anchor]] => SelfAnchorHref("anchor")
  case class SelfAnchorHref(anchor: String) extends NamuHref {
    val value: String = "#" + anchor
  }
  // [[../]] => SuperDocHref
  case object SuperDocHref extends NamuHref {
    val value: String = "../"
  }
  // [[/child]] => ChildDocHref("child")
  case class ChildDocHref(childHref: NamuHref) extends NamuHref {
    val value: String = s"/${childHref.value}"
  }

  // 최대한 Paragraph라는 Seq[NamuMark] Wrapper를 줄이기
  private[biryo] def pbResolver(pb: ParagraphBuilder): NamuMark = {
    if (pb.markList.isEmpty) {
      RawString(pb.sb.toString)
    } else if (pb.sb.isEmpty) {
      if (pb.markList.length == 1)
        pb.markList.head
      else
        Paragraph(pb.markList)
    } else {
      Paragraph(pb.markList :+ RawString(pb.sb.toString))
    }
  }

  private[biryo] def pbMerger(pb: ParagraphBuilder, lineObj: NamuMark): ParagraphBuilder = {
    if (pb.sb.isEmpty) {
      pb.markList match {
        case (p: Paragraph) +: Seq() => ParagraphBuilder(p.valueSeq :+ lineObj, pb.sb)
        case _ => ParagraphBuilder(pb.markList :+ lineObj, pb.sb)
      }
    } else {
      ParagraphBuilder(
        pb.markList :+ RawString(pb.sb.toString) :+ lineObj,
        new StringBuilder
      )
    }
  }
}

