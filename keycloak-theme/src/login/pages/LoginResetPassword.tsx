import { useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldGroup, Field, FieldLabel, FieldDescription } from "@/components/ui/field";
import { cn } from "@/lib/utils";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";

export default function LoginResetPassword(props: PageProps<Extract<KcContext, { pageId: "login-reset-password.ftl" }>, I18n>) {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { kcClsx } = getKcClsx({
    doUseDefaultCss,
    classes
  });

  const { realm, url, messagesPerField } = kcContext;

  const { msg, msgStr } = i18n;

  const [isSubmitDisabled, setIsSubmitDisabled] = useState(false);

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss: false, classes }}
      displayMessage={false}
      headerNode={msg("emailForgotTitle")}
      displayInfo={false}
      infoNode={null}
    >
      <div id="kc-form">
        <div id="kc-form-wrapper">
          <form
            id="kc-reset-password-form"
            onSubmit={() => {
              setIsSubmitDisabled(true);
              return true;
            }}
            action={url.loginAction}
            method="post"
            className={cn("flex flex-col gap-6", "kc-form-login")}
          >
            <FieldGroup>
              {messagesPerField.existsError("username") && (
                <div className="text-destructive text-sm text-center" role="alert">
                  {messagesPerField.getFirstError("username")}
                </div>
              )}

              <Field>
                <FieldLabel htmlFor="username">
                  {!realm.loginWithEmailAllowed
                    ? msg("username")
                    : !realm.registrationEmailAsUsername
                      ? msg("usernameOrEmail")
                      : msg("email")
                  }
                </FieldLabel>
                <Input
                  tabIndex={1}
                  id="username"
                  className={kcClsx("kcInputClass")}
                  name="username"
                  type="text"
                  autoFocus
                  autoComplete="username"
                  aria-invalid={messagesPerField.existsError("username")}
                  placeholder={!realm.loginWithEmailAllowed ? "Enter your username" : "m@example.com"}
                  required
                />
              </Field>

              <Field>
                <Button
                  tabIndex={2}
                  disabled={isSubmitDisabled}
                  className={cn(kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass"), "w-full")}
                  name="submit"
                  id="kc-form-submit"
                  type="submit"
                >
                  {msgStr("doSubmit")}
                </Button>
              </Field>

              <FieldDescription className="text-center">
                <a
                  href={url.loginUrl}
                  tabIndex={3}
                  className="text-sm underline-offset-4 hover:underline"
                >
                  {msg("backToLogin")}
                </a>
              </FieldDescription>
            </FieldGroup>
          </form>
        </div>
      </div>
    </Template>
  );
}