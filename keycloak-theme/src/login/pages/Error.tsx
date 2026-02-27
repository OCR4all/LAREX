import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Icon } from "@iconify/react";
import { FieldGroup, Field } from "@/components/ui/field";

export default function Error(props: PageProps<Extract<KcContext, { pageId: "error.ftl" }>, I18n>) {
  const { kcContext, i18n, Template, classes } = props;

  const { message, client, skipLink } = kcContext;

  const { msg } = i18n;

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={false}
      classes={classes}
      displayMessage={false}
      headerNode={msg("errorTitle")}
    >
      <div className="flex flex-col gap-6">
        <FieldGroup>
          <Field>
            <Alert variant="error">
              <Icon icon="lucide:alert-triangle" />
              <AlertDescription dangerouslySetInnerHTML={{ __html: message.summary }} />
            </Alert>
          </Field>

          {!skipLink && client !== undefined && client.baseUrl !== undefined && (
            <Field>
              <Button asChild className="w-full">
                <a id="backToApplication" href={client.baseUrl}>
                  {msg("backToApplication")}
                </a>
              </Button>
            </Field>
          )}
        </FieldGroup>
      </div>
    </Template>
  );
}