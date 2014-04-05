package securesocial.core

import _root_.java.util.Properties
import play.api.Play.current
import net.tanesha.recaptcha.{ReCaptchaImpl, ReCaptchaFactory}
import play.api.mvc.{Request, AnyContent}


object Captcha
{
  val secureRecaptchaServer = "https://www.google.com/recaptcha/api"

  def validateCaptcha(request: Request[AnyContent]): Boolean = {
    if (Captcha.isEnabled) {
      val question = request.body.asFormUrlEncoded.get("recaptcha_challenge_field")
      val answer = request.body.asFormUrlEncoded.get("recaptcha_response_field")
      if (!question.isEmpty && !answer.isEmpty) {
        check(question.head, answer.head)
      }
      else {
        false
      }
    }
    else {
      true
    }
  }
  def isEnabled: Boolean = {
    current.configuration.getBoolean("recaptcha.enabled").getOrElse(false)
  }
  def publicKey(): String = {
    current.configuration.getString("recaptcha.publickey").get
  }
  def privateKey(): String = {
    current.configuration.getString("recaptcha.privatekey").get
  }
  def address(): String = {
    current.configuration.getString("recaptcha.address").get
  }
  def theme(): String = {
    current.configuration.getString("recaptcha.theme").getOrElse("red")
  }
  def isSecure: Boolean = {
    current.configuration.getBoolean("securesocial.ssl").getOrElse(false)
  }

  def check(challenge: String, response: String): Boolean = {
    val reCaptcha = new ReCaptchaImpl()
    reCaptcha.setPrivateKey(privateKey())
    val reCaptchaResponse = reCaptcha.checkAnswer(address(), challenge, response)
    reCaptchaResponse.isValid
  }

  def render(): String = {
    if (isEnabled) {
      val properties = new Properties()
      properties.put("theme", theme())
      if (isSecure) {
        val recaptcha = new ReCaptchaImpl
        recaptcha.setIncludeNoscript(false)
        recaptcha.setPrivateKey(privateKey())
        recaptcha.setPublicKey(publicKey())
        recaptcha.setRecaptchaServer(secureRecaptchaServer)
        recaptcha.createRecaptchaHtml(null, properties)
      }
      else {
        ReCaptchaFactory.newReCaptcha(publicKey(), privateKey(), false).createRecaptchaHtml(null, properties)
      }
    } else {
      ""
    }
  }
}
