/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.controllers

import _root_.java.util.UUID
import play.api.mvc.{RequestHeader, Result, Action, Controller}
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.Play
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core._
import com.typesafe.plugin._
import Play.current
import securesocial.core.providers.utils._
import org.joda.time.DateTime
import play.api.i18n.Messages
import securesocial.core.providers.Token
import scala.Some
import securesocial.core.IdentityId
import scala.language.reflectiveCalls


/**
 * A controller to handle user registration.
 *
 */
object Registration extends Controller {
  private val logger = play.api.Logger("securesocial.controllers.Registration")

  val providerId = UsernamePasswordProvider.UsernamePassword
  val UserNameAlreadyTaken = "securesocial.signup.userNameAlreadyTaken"
  val PasswordsDoNotMatch = "securesocial.signup.passwordsDoNotMatch"
  val ThankYouCheckEmail = "securesocial.signup.thankYouCheckEmail"
  val InvalidLink = "securesocial.signup.invalidLink"
  val SignUpDone = "securesocial.signup.signUpDone"
  val PasswordUpdated = "securesocial.password.passwordUpdated"
  val ErrorUpdatingPassword = "securesocial.password.error"

  val UserName = "userName"
  val FirstName = "firstName"
  val LastName = "lastName"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"
  val Email = "email"
  val Success = "success"
  val Error = "error"

  val TokenDurationKey = "securesocial.userpass.tokenDuration"
  val RegistrationEnabled = "securesocial.registrationEnabled"
  val DefaultDuration = 60
  val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
  val NotificationEmail = Play.current.configuration.getString("notification.email")

  /** The redirect target of the handleStartSignUp action. */
  val onHandleStartSignUpGoTo = stringConfig("securesocial.onStartSignUpGoTo", RoutesHelper.login().url)
  /** The redirect target of the handleSignUp action. */
  val onHandleSignUpGoTo = stringConfig("securesocial.onSignUpGoTo", RoutesHelper.login().url)
  val onHandleSignUpGoToOpt = Play.current.configuration.getString("securesocial.onSignUpGoTo")
  /** The redirect target of the handleStartResetPassword action. */
  val onHandleStartResetPasswordGoTo = stringConfig("securesocial.onStartResetPasswordGoTo", RoutesHelper.login().url)
  /** The redirect target of the handleResetPassword action. */
  val onHandleResetPasswordGoTo = stringConfig("securesocial.onResetPasswordGoTo", RoutesHelper.login().url)

  lazy val registrationEnabled = current.configuration.getBoolean(RegistrationEnabled).getOrElse(true)

  val EmailRegex = """^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,4}$""".r

  private def stringConfig(key: String, default: => String) = {
    Play.current.configuration.getString(key).getOrElse(default)
  }

  case class RegistrationInfo(userName: Option[String], firstName: String, lastName: String, password: String)

  val formWithUsername = Form[RegistrationInfo](
    mapping(
      UserName -> nonEmptyText.verifying( Messages(UserNameAlreadyTaken), userName => {
          UserService.find(IdentityId(userName,providerId)).isEmpty
      }),
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(PasswordValidator.constraint),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
    )
    // binding
    ((userName, firstName, lastName, password) => RegistrationInfo(Some(userName), firstName, lastName, password._1))
    // unbinding
    (info => Some(info.userName.getOrElse(""), info.firstName, info.lastName, ("", "")))
  )

  val formWithoutUsername = Form[RegistrationInfo](
    mapping(
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying( PasswordValidator.constraint ),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
    )
      // binding
      ((firstName, lastName, password) => RegistrationInfo(None, firstName, lastName, password._1))
      // unbinding
      (info => Some(info.firstName, info.lastName, ("", "")))
  )

  val form = if ( UsernamePasswordProvider.withUserNameSupport ) formWithUsername else formWithoutUsername

  val startForm = Form (
    Email -> email.verifying( nonEmpty )
  )

  val changePasswordForm = Form (
    Password ->
      tuple(
        Password1 -> nonEmptyText.verifying( PasswordValidator.constraint ),
        Password2 -> nonEmptyText
      ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
  )

  /**
   * Starts the sign up process
   */
  def startSignUp = Action { implicit request =>
    if (registrationEnabled) {
      if ( SecureSocial.enableRefererAsOriginalUrl ) {
        SecureSocial.withRefererAsOriginalUrl(Ok(use[TemplatesPlugin].getStartSignUpPage(startForm)))
      } else {
        Ok(use[TemplatesPlugin].getStartSignUpPage(startForm))
      }
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  private def createToken(email: String, isSignUp: Boolean): (String, Token) = {
    val uuid = UUID.randomUUID().toString
    val now = DateTime.now

    val token = Token(
      uuid, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp
    )
    UserService.save(token)
    (uuid, token)
  }

  def handleStartSignUp = Action { implicit request =>
    if (registrationEnabled) {
      if (!Captcha.validateCaptcha(request)) {
        Redirect(RoutesHelper.startSignUp()).flashing(Error -> "Bad captcha")
      }
      else {
        startForm.bindFromRequest.fold (
          errors => {
            BadRequest(use[TemplatesPlugin].getStartSignUpPage(errors))
          },
          email => {
            if (EmailRegex.findAllMatchIn(email).isEmpty) {
              Redirect(RoutesHelper.startSignUp()).flashing(Error -> "Please enter an email address")
            } else {
              // check if there is already an account for this email address
              UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
                case Some(user) => {
                  // user signed up already, send an email offering to login/recover password
                  Mailer.sendAlreadyRegisteredEmail(user)
                }
                case None => {
                  val token = createToken(email, isSignUp = true)
                  Mailer.sendSignUpEmail(email, token._1)
                }
              }
            }
            Redirect(onHandleStartSignUpGoTo).flashing(Success -> Messages(ThankYouCheckEmail), Email -> email)
          }
        )
      }
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  /**
   * Renders the sign up page
   * @return
   */
  def signUp(token: String) = Action { implicit request =>
    if (registrationEnabled) {
      logger.debug("[securesocial] trying sign up with token %s".format(token))
      executeForToken(token, true, { _ =>
        Ok(use[TemplatesPlugin].getSignUpPage(form, token))
      })
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  private def executeForToken(token: String, isSignUp: Boolean, f: Token => Result)(implicit request: RequestHeader): Result = {
    UserService.findToken(token) match {
      case Some(t) if !t.isExpired && t.isSignUp == isSignUp => {
        f(t)
      }
      case _ => {
        val to = if ( isSignUp ) RoutesHelper.startSignUp() else RoutesHelper.startResetPassword()
        Redirect(to).flashing(Error -> Messages(InvalidLink))
      }
    }
  }

  /**
   * Handles posts from the sign up page
   */
  def handleSignUp(token: String) = Action { implicit request =>
    if (registrationEnabled) {
      executeForToken(token, true, { t =>
        form.bindFromRequest.fold (
          errors => {
            logger.debug("[securesocial] errors " + errors)
            BadRequest(use[TemplatesPlugin].getSignUpPage(errors, t.uuid))
          },
          info => {
            val id = if ( UsernamePasswordProvider.withUserNameSupport ) info.userName.get else t.email
            val identityId = IdentityId(id, providerId)
            val user = SocialUser(
              identityId,
              info.firstName,
              info.lastName,
              "%s %s".format(info.firstName, info.lastName),
              Some(t.email),
              GravatarHelper.avatarFor(t.email),
              AuthenticationMethod.UserPassword,
              passwordInfo = Some(Registry.hashers.currentHasher.hash(info.password))
            )
            val saved = UserService.save(user)
            UserService.deleteToken(t.uuid)
            if ( UsernamePasswordProvider.sendWelcomeEmail ) {
              Mailer.sendWelcomeEmail(saved)
            }
            NotificationEmail.map { notificationEmail =>
              Mailer.sendNotificationEmail(notificationEmail, user, request)
            }
            val eventSession = Events.fire(new SignUpEvent(user)).getOrElse(session)
            if ( UsernamePasswordProvider.signupSkipLogin ) {
              val authResult = ProviderController.completeAuthentication(user, eventSession).flashing(Success -> Messages(SignUpDone))
              onHandleSignUpGoToOpt.map { targetUrl =>
                authResult.withHeaders(LOCATION -> targetUrl)
              } getOrElse authResult
            } else {
              Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(SignUpDone)).withSession(eventSession)
            }
          }
        )
      })
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  def startResetPassword = Action { implicit request =>
    Ok(use[TemplatesPlugin].getStartResetPasswordPage(startForm ))
  }

  /*

  def handleStartResetPassword = Action { implicit request =>
    if (!Captcha.validateCaptcha(request)) {
      Redirect(RoutesHelper.startResetPassword()).flashing(Error -> "Bad captcha")
    }
    else {
      startForm.bindFromRequest.fold (
        errors => {
          BadRequest(use[TemplatesPlugin].getStartResetPasswordPage(request , errors))
        },
        email => {
          if (EmailRegex.findAllMatchIn(email).isEmpty) {
            Redirect(RoutesHelper.startResetPassword()).flashing(Error -> "Please enter an email address")
          }
          else {
            UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
              case Some(user) => {
                val token = createToken(email, isSignUp = false)
                Mailer.sendPasswordResetEmail(user, token._1)
              }
              case None => {
                Logger.logger.info("User is trying to restore their password using '{}' but we don't have it in our database", email)
              }
            }
            Redirect(onHandleStartResetPasswordGoTo).flashing(Success -> Messages(ThankYouCheckEmail))
          }
        }
      )
    }
  }
   */
  def handleStartResetPassword = Action { implicit request =>
    if (!Captcha.validateCaptcha(request)) {
      Redirect(RoutesHelper.startResetPassword()).flashing(Error -> "Bad captcha")
    }
    else {
      startForm.bindFromRequest.fold (
        errors => {
          BadRequest(use[TemplatesPlugin].getStartResetPasswordPage(errors))
        },
        email => {
          if (EmailRegex.findAllMatchIn(email).isEmpty) {
            Redirect(RoutesHelper.startResetPassword()).flashing(Error -> "Please enter an email address")
          }
          else {
            UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
              case Some(user) => {
                val token = createToken(email, isSignUp = false)
                Mailer.sendPasswordResetEmail(user, token._1)
              }
              case None => {
                Mailer.sendUnkownEmailNotice(email)
              }
            }
            Redirect(onHandleStartResetPasswordGoTo).flashing(Success -> Messages(ThankYouCheckEmail))
          }
        }
      )
    }
  }

  def resetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      Ok(use[TemplatesPlugin].getResetPasswordPage(changePasswordForm, token))
    })
  }

  def handleResetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t=>
      changePasswordForm.bindFromRequest.fold( errors => {
        BadRequest(use[TemplatesPlugin].getResetPasswordPage(errors, token))
      },
      p => {
        val (toFlash, eventSession) = UserService.findByEmailAndProvider(t.email, UsernamePasswordProvider.UsernamePassword) match {
          case Some(user) => {
            val hashed = Registry.hashers.currentHasher.hash(p._1)
            val updated = UserService.save( SocialUser(user).copy(passwordInfo = Some(hashed)) )
            UserService.deleteToken(token)
            Mailer.sendPasswordChangedNotice(updated)
            val eventSession = Events.fire(new PasswordResetEvent(updated))
            ( Success -> Messages(PasswordUpdated), eventSession)
          }
          case _ => {
            logger.error("[securesocial] could not find user with email %s during password reset".format(t.email))
            ( Error -> Messages(ErrorUpdatingPassword), None)
          }
        }
        val result = Redirect(onHandleResetPasswordGoTo).flashing(toFlash)
        eventSession.map( result.withSession ).getOrElse(result)
      })
    })
  }
}
