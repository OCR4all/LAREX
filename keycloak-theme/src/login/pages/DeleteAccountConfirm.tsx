import { Icon } from "@iconify/react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldGroup } from "@/components/ui/field";

export default function DeleteAccountConfirm(
  props: PageProps<Extract<KcContext, { pageId: "delete-account-confirm.ftl" }>, I18n>
) {
  const { kcContext, i18n, Template, classes } = props;
  const { url, triggered_from_aia } = kcContext;
  const { msg, msgStr } = i18n;

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={false}
      classes={classes}
      displayMessage={false}
      headerNode={msg("deleteAccountConfirm")}
    >
      <form
        id="kc-delete-account-confirm"
        action={url.loginAction}
        method="post"
      >
        <FieldGroup>
          <Field>
            <Alert variant="danger">
              <Icon icon="lucide:triangle-alert" />
              <AlertTitle>{msg("irreversibleAction")}</AlertTitle>
              <AlertDescription>{msg("finalDeletionConfirmation")}</AlertDescription>
            </Alert>
          </Field>

          <Field className="space-y-3">
            <p className="text-sm font-medium text-foreground">{msg("deletingImplies")}</p>
            <ul className="list-disc space-y-2 pl-5 text-sm text-muted-foreground">
              <li>{msg("loggingOutImmediately")}</li>
              <li>{msg("errasingData")}</li>
              <li>{msg("accountUnusable")}</li>
            </ul>
          </Field>

          <Field className="grid gap-3 sm:grid-cols-2">
            <Button
              id="kc-delete-account"
              type="submit"
              variant="destructive"
              className="w-full"
            >
              <Icon icon="lucide:trash-2" />
              {msgStr("doConfirmDelete")}
            </Button>

            {triggered_from_aia && (
              <Button
                id="kc-cancel-delete-account"
                type="submit"
                name="cancel-aia"
                value="true"
                variant="outline"
                className="w-full"
              >
                {msgStr("doCancel")}
              </Button>
            )}
          </Field>
        </FieldGroup>
      </form>
    </Template>
  );
}
