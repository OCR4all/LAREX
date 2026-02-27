import React, { useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldGroup, Field, FieldLabel } from "@/components/ui/field";
import { cn } from "@/lib/utils";
import { useIsPasswordRevealed } from "keycloakify/tools/useIsPasswordRevealed";
import { clsx } from "keycloakify/tools/clsx";
import { cloneElement } from "react";
import { Icon } from "@iconify/react";
import type { KcClsx } from "keycloakify/login/lib/kcClsx";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";

export default function LoginUpdatePassword(props: PageProps<Extract<KcContext, { pageId: "login-update-password.ftl" }>, I18n>) {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { kcClsx } = getKcClsx({
    doUseDefaultCss,
    classes
  });

  const { url } = kcContext;

  const { msg, msgStr } = i18n;

  const [isLoginButtonDisabled, setIsLoginButtonDisabled] = useState(false);

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss: false, classes }}
      displayMessage={false}
      headerNode={msg("doSubmit")}
    >
      <div className="flex flex-col gap-6">
        <form id="kc-update-password-form" action={url.loginAction} method="post">
          <FieldGroup>
            
            <Field>
              <FieldLabel htmlFor="password-new">{msg("passwordNew")}</FieldLabel>
              <PasswordWrapper kcClsx={kcClsx} i18n={i18n} passwordInputId="password-new">
                <Input
                  id="password-new"
                  name="password-new"
                  type="password"
                  autoComplete="new-password"
                  required
                />
              </PasswordWrapper>
            </Field>

            <Field>
              <FieldLabel htmlFor="password-confirm">{msg("passwordConfirm")}</FieldLabel>
              <PasswordWrapper kcClsx={kcClsx} i18n={i18n} passwordInputId="password-confirm">
                <Input
                  id="password-confirm"
                  name="password-confirm"
                  type="password"
                  autoComplete="new-password"
                  required
                />
              </PasswordWrapper>
            </Field>

            <Field>
              <Button
                type="submit"
                disabled={isLoginButtonDisabled}
                className={cn(kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass"), "w-full")}
                onClick={() => setIsLoginButtonDisabled(true)}
              >
                {msgStr("doSubmit")}
              </Button>
            </Field>
          </FieldGroup>
        </form>
      </div>
    </Template>
  );
}

function PasswordWrapper(props: {
  kcClsx: KcClsx;
  i18n: I18n;
  passwordInputId: string;
  children: React.ReactElement<{ className?: string; type?: string }>;
}) {
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
