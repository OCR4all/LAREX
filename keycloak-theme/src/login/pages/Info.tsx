import type { PageProps } from "keycloakify/login/pages/PageProps";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Icon } from "@iconify/react";
import { FieldGroup, Field } from "@/components/ui/field";

export default function Info(props: PageProps<Extract<KcContext, { pageId: "info.ftl" }>, I18n>) {
  const { kcContext, i18n, Template, classes } = props;

  const { messageHeader, message, requiredActions, client, skipLink, pageRedirectUri, actionUri } = kcContext;

  const { advancedMsgStr, msg } = i18n;

  const summaryHtml = kcSanitize(
    (() => {
      let html = message.summary?.trim() ?? "";

      if (requiredActions && requiredActions.length > 0) {
        html += " <b>";
        html += requiredActions.map(requiredAction => advancedMsgStr(`requiredAction.${requiredAction}`)).join(", ");
        html += "</b>";
      }

      return html;
    })()
  );

  const headerHtml = kcSanitize(messageHeader ?? message.summary);

  const link = (() => {
    if (skipLink) {
      return null;
    }
    if (pageRedirectUri) {
      return { href: pageRedirectUri, label: msg("backToApplication") };
    }
    if (actionUri) {
      return { href: actionUri, label: msg("proceedWithAction") };
    }
    if (client?.baseUrl) {
      return { href: client.baseUrl, label: msg("backToApplication") };
    }
    return null;
  })();

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss: false, Template, classes }}
      displayMessage={false}
      headerNode={<span dangerouslySetInnerHTML={{ __html: headerHtml }} />}
    >
      <div className="flex flex-col gap-6">
        <FieldGroup>
          <Field>
            <Alert variant="info">
              <Icon icon="lucide:info" />
              <AlertDescription dangerouslySetInnerHTML={{ __html: summaryHtml }} />
            </Alert>
          </Field>

          {link !== null && (
            <Field>
              <Button asChild className="w-full">
                <a id="backToApplication" href={link.href}>
                  {link.label}
                </a>
              </Button>
            </Field>
          )}
        </FieldGroup>
      </div>
    </Template>
  );
}
