package controllers

import play.api.http.HeaderNames
import play.api.mvc.{Request, AnyContent}
import play.api.test.{PlaySpecification, FakeApplication, FakeRequest}
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.matcher.ShouldMatchers
import play.api.test.WithApplication
import org.junit.runner.RunWith
import scala.xml.Elem
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.Files.TemporaryFile
import play.api.test.FakeHeaders
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import scala.slick.driver.H2Driver
import mining.io.UserFactory
import play.api.test.PlaySpecification
import play.api.test.FakeRequest
import models.AuthUser
import scala.util.Properties
import play.api.Application
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.H2Driver.api._
import mining.io.slick.SlickUserDAO
import mining.util.DirectoryUtil


@RunWith(classOf[JUnitRunner])
class UserControllerSpec extends PlaySpecification with ShouldMatchers{
  Properties.setProp("runMode", "test")
  def sampleOpml:String = {
    val dom:Elem  = 
  <opml version="1.0">
		<head><title>Sample</title></head>
		<body>
			<outline text="We need more..." title="We need more..." type="rss"
				xmlUrl="http://blog.csdn.net/zhuliting/rss/list" htmlUrl="http://blog.csdn.net/zhuliting"/>
			<outline title="FlexBlogs" text="FlexBlogs">
				<outline text="AdobeAll-Bee" title="AdobeAll-Bee" type="rss"
					xmlUrl="http://www.beedigital.net/blog/?feed=rss2" htmlUrl="http://www.beedigital.net/blog"/>
			</outline>
		</body>
	</opml>
    	  
    	dom.toString
  }
  def cleanUpUserOpmlFolder() = {
    val opmlFolderPath = DirectoryUtil.pathFromProject("target","useropml")
    val omplFolder = new File(opmlFolderPath)
    for (subfile <- omplFolder.listFiles()) subfile.delete()
  }
  
  def userController(implicit app: Application) = {
      val app2ApplicationController = Application.instanceCache[controllers.UserController]
      app2ApplicationController(app)
  }

  val zhangsan = UserFactory.newUser(1L, "zhangsan@readmine.co") 
  //val samplefeed = "http://blog.csdn.net/zhuliting/rss/list"
  val sampleFeed = "http://coolshell.cn/feed"
  var sampleStory = "http://coolshell.cn/articles/11170.html"
  
  val fakeApplication=FakeApplication()
  
  step{
    cleanUpUserOpmlFolder
    AuthUser.testUser = Some(zhangsan);
  }
     
  sequential
  "when zhangsan first list feeds he should see empty" in new WithApplication {
    val request = FakeRequest( GET, "/user/list-feeds")
    val jsonresult = userController.listFeeds()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsJson(jsonresult)
    ( result \ "Opml" ).as[JsArray].value.size must be equalTo( 0 )
    ( result \ "Stories" ).as[JsArray].value.size must be equalTo( 0 )
    ( result \ "feeds" ).as[JsArray].value.size must be equalTo( 0 )
  }
  
  "zhangsan should be able to import opml" in new WithApplication {
    val db = Database.forConfig("h2mem1")
    val userDAO = SlickUserDAO(db)
    userDAO.saveUser(zhangsan)
    val tempfile = File.createTempFile("sample", ".opml"); 
    tempfile.deleteOnExit();
    val out = new BufferedWriter(new FileWriter(tempfile));
    out.write( sampleOpml );
    out.close();
    val data = new MultipartFormData(  
        Map(), 
        List(  FilePart("file", "sample.opml", Some("text/xml"), TemporaryFile(tempfile)  ) ), 
        List(),
        List()
    )
    val jsonresult = userController.importOPML()( FakeRequest(POST, "/user/import-opml",FakeHeaders(),data))
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
  }
  
  "zhangsan should be able to export opml" in new WithApplication() {
    val request = FakeRequest( GET, "/user/export-opml")
    //val Some( xmlresult ) = route( request )
    val xmlresult = userController.exportOPML()(request)
    status(xmlresult) must be equalTo OK
    contentType(xmlresult).get must equalTo("text/html")
    contentAsString(xmlresult ) must contain( "http://www.beedigital.net/blog/?feed=rss2" )
  }
  
  "zhangsan should be able to add subscription" in new WithApplication {
    val request = FakeRequest( POST, "/user/add-subscription")
    		.withHeaders( CONTENT_TYPE -> "application/x-www-form-urlencoded" )
    		//.withFormUrlEncodedBody( "url"->"http://coolshell.cn/feed")
    		.withFormUrlEncodedBody( "url"->sampleFeed)
    //val Some( jsonresult ) = route( request )
    val jsonresult = userController.addSubscription()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    contentAsString(jsonresult ) must contain( "Subscripton Added" )
  }
  
  "zhangsan should be able to list feeds" in new WithApplication {
    val request = FakeRequest( GET, "/user/list-feeds")
    val jsonresult = userController.listFeeds()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsJson(jsonresult)
    ( result \ "Opml" ).as[JsArray].value.size must be greaterThan( 0 )
    ( result \ "Stories" ).as[JsArray].value.size must be greaterThan( 0 )
    ( result \ "feeds" ).as[JsArray].value.size must be greaterThan( 0 )
  }
  
  "zhangsan should be able to upload opml" in new WithApplication {
    val request1 = FakeRequest( GET, "/user/list-feeds")
    val lfresult = userController.listFeeds()(request1)
    val lfjresult = contentAsJson(lfresult)
    
    val jsonparams = Json.obj( "opml"-> (lfjresult\"Opml").as[JsArray] )
    val request2 = FakeRequest( POST, "/user/upload-opml")
    	.withJsonBody( jsonparams).withHeaders(CONTENT_TYPE->"application/json")
    	
    val jsonresult = userController.uploadOPML()(request2)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
  }
  
 
  "zhangsan should be able to save his preferences" in new WithApplication {
   
    val jsonparams = Json.parse("""{"options":{"folderClose":{},"nav":true,"expanded":false,"mode":"all","sort":"newest","hideEmpty":false,"scrollRead":false}}""")
    val request = FakeRequest( POST, "/user/save-options").withJsonBody(jsonparams )
    				
    val jsonresult = userController.saveOptions()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsString(jsonresult)
    result must contain ("1")
  }
  
  "zhangsan should be able to star one of the story he read" in new WithApplication {
     //star and share action ... can they be treated as the same  
    val jsonparams = Json.obj( 
    		"feed"->"http://blog.csdn.net/zhuliting/rss/list",
    		"story"->"http://blog.csdn.net/zhuliting/blog/1",
    		"del"->""
        )
    val request = FakeRequest( GET, "/user/set-star").withJsonBody(jsonparams )
    				
    val jsonresult = userController.setStar()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsString(jsonresult)
    result must contain ("1")
  }
  
  "zhangsan should be able to mark one story he read as read" in new WithApplication {
     //there should be different markread [ nature read; markread; markunread ] 
    val item = Json.obj( 
    		"Feed"->"http://blog.csdn.net/zhuliting/rss/list",
    		"Story"->"http://blog.csdn.net/zhuliting/blog/1"
        )
    val jsonparams = JsArray( List(item, item ))
    val request = FakeRequest( GET, "/user/mark-read").withJsonBody(jsonparams )
    				
    val jsonresult = userController.markRead()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsString(jsonresult)
    result must contain ("1")
  }


  
  "zhangsan should be able to get a list of stories of a feed" in new WithApplication {
    val jsonparams = Json.obj( 
    		"f"->sampleFeed,
    		"c"->"0"
        )
    val request = FakeRequest( POST, "/user/get-feed").withJsonBody(jsonparams )
    				
    val jsonresult = userController.getFeed()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsJson(jsonresult)
    ( result \ "Cursor" ).as[String]  must contain ("1")
    ( result \ "Stories" ).as[JsArray].value.size must be greaterThan( 0 )
    //( result \ "feeds" ).as[JsArray].value.size must be greaterThan( 0 )
    val page0 = ( result \ "Stories" ).as[List[JsValue]]
    val page0head = page0.head
    
    sampleStory = (page0head \ "Id").as[String]
    
    val jparam2 = Json.obj( 
    		"f"->sampleFeed,
    		"c"->"1"
        )
    val rq2 = FakeRequest( POST, "/user/get-feed").withJsonBody(jparam2 )		
    val jr2 = userController.getFeed()(rq2)
    val r2 = contentAsJson(jr2)
    val page1 = ( r2 \ "Stories" ).as[List[JsValue]]
    val page1head = page1.head
    ( page0head \ "Link" ).as[String] must not equalTo ( page1head \ "Link" ).as[String]
  }
  
  "zhangsan should be able to get a content of a feed" in new WithApplication {
    val jsonparams = Json.parse(s"""[{"Feed":"$sampleFeed","Story":"$sampleStory"}]""") 
    val request = FakeRequest( GET, "/user/get-contents").withJsonBody(jsonparams )
    				
    val jsonresult = userController.getContents()(request)
    status(jsonresult) must be equalTo OK
    contentType(jsonresult).get must equalTo("application/json")
    val result = contentAsString(jsonresult)
    result must contain ("42")
  }
  
  /*"zhangsan should be able to get a list of story contents of a feed" in {
     //pagnation should be considered    
  }  
  
  "zhangsan should be able to mark stories of one feed he read as all read" in {
     //there's no such API ... TODO:   
     //but should be considered because passing all stories in feed contains more traffic
  }*/
}