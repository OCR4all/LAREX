import type { JSX } from "keycloakify/tools/JSX";
import { cloneElement, useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { useIsPasswordRevealed } from "keycloakify/tools/useIsPasswordRevealed";
import { clsx } from "keycloakify/tools/clsx";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx, type KcClsx } from "keycloakify/login/lib/kcClsx";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Field, FieldLabel, FieldDescription, FieldGroup, FieldSeparator } from "@/components/ui/field";
import { Icon } from "@iconify/react";
import { Checkbox } from "@/components/ui/checkbox";

const socialProviderIcons: Record<string, string> = {
  google: "simple-icons:google",
  microsoft: "simple-icons:microsoft",
  facebook: "simple-icons:facebook",
  instagram: "simple-icons:instagram",
  twitter: "simple-icons:x",
  linkedin: "simple-icons:linkedin",
  stackoverflow: "simple-icons:stackoverflow",
  github: "simple-icons:github",
  gitlab: "simple-icons:gitlab",
  bitbucket: "simple-icons:bitbucket",
  paypal: "simple-icons:paypal",
  openshift: "simple-icons:redhatopenshift",
  apple: "simple-icons:apple",
  discord: "simple-icons:discord",
  slack: "simple-icons:slack",
  spotify: "simple-icons:spotify",
  twitch: "simple-icons:twitch"
};

function getSocialProviderIcon(alias: string): string {
  return socialProviderIcons[alias.toLowerCase()] ?? "lucide:log-in";
}

export default function Login(props: PageProps<Extract<KcContext, { pageId: "login.ftl" }>, I18n>) {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { kcClsx } = getKcClsx({
    doUseDefaultCss,
    classes
  });

  const { social, realm, url, usernameHidden, login, auth, messagesPerField } = kcContext;

  const { msg, msgStr } = i18n;

  const [isLoginButtonDisabled, setIsLoginButtonDisabled] = useState(false);

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      displayMessage={false} // We handle messages in the form itself
      headerNode={msg("doLogIn")}
      displayInfo={false} // Handle registration in the form
      infoNode={null}
      socialProvidersNode={null}
    >
      <div id="kc-form">
        <div id="kc-form-wrapper">
          {realm.password && (
            <form
              id="kc-form-login"
              onSubmit={() => {
                setIsLoginButtonDisabled(true);
                return true;
              }}
              action={url.loginAction}
              method="post"
              className={cn("flex flex-col gap-6", "kc-form-login")}
            >
              <FieldGroup>
                {messagesPerField.existsError("username", "password") && (
                  <div className="text-destructive text-sm text-center" role="alert">
                    {kcSanitize(messagesPerField.getFirstError("username", "password"))}
                  </div>
                )}

                {!usernameHidden && (
                  <Field>
                    <FieldLabel htmlFor="username">
                      {!realm.loginWithEmailAllowed ? msg("username") : !realm.registrationEmailAsUsername ? msg("usernameOrEmail") : msg("email")}
                    </FieldLabel>
                    <Input
                      tabIndex={2}
                      id="username"
                      className={kcClsx("kcInputClass")}
                      name="username"
                      defaultValue={login.username ?? ""}
                      type="text"
                      autoFocus
                      autoComplete="username"
                      aria-invalid={messagesPerField.existsError("username", "password")}
                      placeholder={!realm.loginWithEmailAllowed ? "Enter your username" : "m@example.com"}
                      required
                    />
                  </Field>
                )}

                <Field>
                  <FieldLabel htmlFor="password">{msg("password")}</FieldLabel>
                  <PasswordWrapper kcClsx={kcClsx} i18n={i18n} passwordInputId="password">
                    <Input
                      tabIndex={3}
                      id="password"
                      className={kcClsx("kcInputClass")}
                      name="password"
                      type="password"
                      autoComplete="current-password"
                      aria-invalid={messagesPerField.existsError("username", "password")}
                      required
                    />
                  </PasswordWrapper>
                </Field>

                {realm.rememberMe && !usernameHidden && (
                  <div className="flex items-center gap-3">
                    <Checkbox tabIndex={5} id="rememberMe" name="rememberMe" defaultChecked={!!login.rememberMe} />
                    <FieldLabel htmlFor="rememberMe" className="text-sm font-normal">
                      {msg("rememberMe")}
                    </FieldLabel>
                  </div>
                )}

                <Field>
                  <input type="hidden" id="id-hidden-input" name="credentialId" value={auth.selectedCredential} />
                  <Button
                    tabIndex={7}
                    disabled={isLoginButtonDisabled}
                    className={cn(kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass"), "w-full")}
                    name="login"
                    id="kc-login"
                    type="submit"
                  >
                    {msgStr("doLogIn")}
                  </Button>
                </Field>

                {realm.resetPasswordAllowed && (
                  <FieldDescription className="text-center">
                    <a
                      href={url.loginResetCredentialsUrl}
                      tabIndex={6}
                      className="text-sm underline-offset-4 hover:underline"
                    >
                      {msg("doForgotPassword")}
                    </a>
                  </FieldDescription>
                )}

                {realm.password && social?.providers !== undefined && social.providers.length !== 0 && (
                  <>
                    <FieldSeparator>Or continue with</FieldSeparator>
                    <div className="space-y-2">
                      {social.providers.map((...[p]) => (
                        <Button key={p.alias} variant="outline" type="button" className="w-full" asChild>
                          <a id={`social-${p.alias}`} href={p.loginUrl} className="flex items-center gap-2">
                            <Icon icon={getSocialProviderIcon(p.alias)} className="size-4" />
                            {kcSanitize(p.displayName)}
                          </a>
                        </Button>
                      ))}
                    </div>
                  </>
                )}
              </FieldGroup>
            </form>
          )}
        </div>
      </div>
    </Template>
  );
}

function PasswordWrapper(props: { kcClsx: KcClsx; i18n: I18n; passwordInputId: string; children: JSX.Element }) {
  const { i18n, passwordInputId, children } = props;

  const { msgStr } = i18n;
  const { isPasswordRevealed, toggleIsPasswordRevealed } = useIsPasswordRevealed({ passwordInputId });

  return (
    <div className="relative">
      {cloneElement(children, {
        type: isPasswordRevealed ? "text" : "password",
        className: clsx(children.props.className, "pr-10") // space for the button
      })}
      <button
        type="button"
        onClick={toggleIsPasswordRevealed}
        aria-label={msgStr(isPasswordRevealed ? "hidePassword" : "showPassword")}
        aria-controls={passwordInputId}
        className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground focus:outline-none"
      >
        {isPasswordRevealed ? <Icon icon="lucide:eye" className="w-4 h-4" /> : <Icon icon="lucide:eye-closed" className="w-4 h-4" />}
      </button>
    </div>
  );
}
