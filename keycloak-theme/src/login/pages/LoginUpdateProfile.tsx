import { useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldGroup, Field, FieldLabel } from "@/components/ui/field";
import { cn } from "@/lib/utils";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";

export default function LoginUpdateProfile(props: PageProps<Extract<KcContext, { pageId: "login-update-profile.ftl" }>, I18n>) {
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
      {...{ kcContext, i18n, doUseDefaultCss: false, Template, classes }}
      displayMessage={false}
      headerNode={msg("doSubmit")}
    >
      <div className="flex flex-col gap-6">
        <form id="kc-update-profile-form" action={url.loginAction} method="post">
          <FieldGroup>
            
            <Field>
              <FieldLabel htmlFor="username">{msg("username")}</FieldLabel>
              <Input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                required
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="email">{msg("email")}</FieldLabel>
              <Input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="firstName">{msg("firstName")}</FieldLabel>
              <Input
                id="firstName"
                name="firstName"
                type="text"
                autoComplete="given-name"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="lastName">{msg("lastName")}</FieldLabel>
              <Input
                id="lastName"
                name="lastName"
                type="text"
                autoComplete="family-name"
              />
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