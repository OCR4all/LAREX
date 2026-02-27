import { useEffect } from "react";
import { clsx } from "keycloakify/tools/clsx";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import { useSetClassName } from "keycloakify/tools/useSetClassName";
import { useInitialize } from "keycloakify/login/Template.useInitialize";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";
import { useDarkMode } from "../lib/useDarkMode";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { Icon } from "@iconify/react";
import { FieldDescription } from "@/components/ui/field";
import AnimatedGradient from "@/components/fancy/background/animated-gradient-with-svg";

import Logo from "./assets/larex_logo.svg";
import themePackage from "../../package.json";

export default function Template(props: TemplateProps<KcContext, I18n>) {
  const {
    displayInfo = false,
    displayMessage = true,
    displayRequiredFields = false,
    socialProvidersNode = null,
    infoNode = null,
    documentTitle,
    bodyClassName,
    kcContext,
    i18n,
    doUseDefaultCss,
    classes,
    children
  } = props;

  const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

  const { msg, msgStr, currentLanguage, enabledLanguages } = i18n;

  const { realm, auth, url, message, isAppInitiatedAction } = kcContext;
  const currentVersion = `v.${themePackage.version}`;

  // Apply dark mode from URL parameters and get state
  const isDark = useDarkMode();

  // Gradient colors based on theme
  const gradientColors = isDark
    ? [
        "#e1552e", // burnt-sienna-500
        "#d23e24", // burnt-sienna-600
        "#af2e1f", // burnt-sienna-700
        "#8c2720", // burnt-sienna-800
        "#33332f", // cararra-800
        "#1c1c19", // cararra-900
      ]
    : [
        "#e1552e", // burnt-sienna-500
        "#d23e24", // burnt-sienna-600
        "#e77952", // burnt-sienna-400
        "#eea683", // burnt-sienna-300
        "#f7f6f3", // cararra-50
        "#f0eee6", // cararra-100
      ];

  const baseBackgroundClass = isDark ? "bg-burnt-sienna-800" : "bg-burnt-sienna-500";

  useEffect(() => {
    document.title = documentTitle ?? msgStr("loginTitle", realm.displayName);
  }, []);

  useSetClassName({
    qualifiedName: "html",
    className: kcClsx("kcHtmlClass")
  });

  useSetClassName({
    qualifiedName: "body",
    className: bodyClassName ?? kcClsx("kcBodyClass")
  });

  const { isReadyToRender } = useInitialize({ kcContext, doUseDefaultCss });

  if (!isReadyToRender) {
    return null;
  }

  return (
    <div className="bg-background flex min-h-svh w-full">
      {/* Left side - Login Form */}
      <div className="flex w-full flex-col md:w-1/2">
        {/* Header with Logo and links */}
        <header className="flex items-center justify-between p-6 lg:p-10">
          <a href="#" className="flex items-center gap-1 font-medium">
            <img src={Logo} alt="LAREX Logo" className="size-8" />
          </a>
          <div className="flex items-center gap-4 text-muted-foreground">
            <a href="#" className="flex items-center gap-1.5 hover:text-foreground transition-colors" aria-label="GitHub">
              <Icon icon="lucide:github" className="size-4" />
            </a>
            <a href="#" className="flex items-center gap-1.5 hover:text-foreground transition-colors" aria-label="Documentation">
              <Icon icon="lucide:book-open" className="size-4" />
            </a>
          </div>
        </header>

        {/* Main content - centered */}
        <div className="flex flex-1 items-center justify-center px-6 lg:px-10">
          <div className="w-full max-w-sm">
            <div className={clsx(kcClsx("kcLoginClass"))}>
              {(() => {
                const node = !(auth !== undefined && auth.showUsername && !auth.showResetCredentials) ? (
                  <div className="flex flex-col items-center gap-2 text-center mb-6">
                    <h1 className="text-xl font-bold">Welcome to {realm.displayName || "LAREX"}</h1>
                    {"password" in realm && realm.registrationAllowed && "registrationUrl" in url && (
                      <FieldDescription>
                        {msg("noAccount")} <a href={url.registrationUrl}>{msg("doRegister")}</a>
                      </FieldDescription>
                    )}
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-2 text-center mb-6">
                    <h1 className="text-xl font-bold">
                      Welcome back, {auth.attemptedUsername}
                      <Button variant="link" asChild className="ml-2 h-auto p-0">
                        <a id="reset-login" href={url.loginRestartFlowUrl} aria-label={msgStr("restartLoginTooltip")}>
                          <Tooltip>
                            <TooltipTrigger>
                              <Icon icon="lucide:refresh-ccw" className="w-4 h-4" />
                            </TooltipTrigger>
                            <TooltipContent>
                              <span className="kc-tooltip-text">{msg("restartLoginTooltip")}</span>
                            </TooltipContent>
                          </Tooltip>
                        </a>
                      </Button>
                    </h1>
                  </div>
                );

                if (displayRequiredFields) {
                  return (
                    <div className={kcClsx("kcContentWrapperClass")}>
                      <div className={clsx(kcClsx("kcLabelWrapperClass"), "subtitle")}>
                        <span className="subtitle">
                          <span className="required">*</span>
                          {msg("requiredFields")}
                        </span>
                      </div>
                      <div className="col-md-10">{node}</div>
                    </div>
                  );
                }

                return node;
              })()}

              {displayMessage && message !== undefined && (message.type !== "warning" || !isAppInitiatedAction) && (
                <Alert variant={message.type} className="mb-6">
                  {message.type === "success" && <Icon icon="lucide:circle-check" />}
                  {message.type === "warning" && <Icon icon="lucide:alert-triangle" />}
                  {message.type === "error" && <Icon icon="lucide:alert-octagon" />}
                  {message.type === "info" && <Icon icon="lucide:info" />}
                  <AlertTitle>Heads up!</AlertTitle>
                  <AlertDescription>{kcSanitize(message.summary)}</AlertDescription>
                </Alert>
              )}

              {children}

              {auth !== undefined && auth.showTryAnotherWayLink && (
                <form id="kc-select-try-another-way-form" action={url.loginAction} method="post" className="mt-4">
                  <div className={kcClsx("kcFormGroupClass")}>
                    <input type="hidden" name="tryAnotherWay" value="on" />
                    <Button
                      variant={"link"}
                      onClick={() => {
                        document.forms["kc-select-try-another-way-form" as never].submit();
                        return false;
                      }}
                    >
                      {msg("doTryAnotherWay")}
                    </Button>
                  </div>
                </form>
              )}

              {socialProvidersNode}

              {displayInfo && <div className="mt-6">{infoNode}</div>}

              {enabledLanguages.length > 1 && (
                <div className="flex justify-center mt-4">
                  <Select
                    onValueChange={selectedTag => {
                      const selectedLang = enabledLanguages.find(lang => lang.languageTag === selectedTag);
                      if (selectedLang?.href) {
                        window.location.href = selectedLang.href;
                      }
                    }}
                    defaultValue={currentLanguage.languageTag}
                  >
                    <SelectTrigger className="w-auto bg-transparent hover:bg-accent text-foreground px-2 py-1 h-auto rounded-md border-none shadow-none focus:ring-0">
                      <SelectValue placeholder="Select a language" />
                    </SelectTrigger>
                    <SelectContent>
                      {enabledLanguages.map(({ languageTag, label, href }) => (
                        <SelectItem key={languageTag} value={languageTag} data-href={href}>
                          {label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <footer className="flex items-center justify-between p-6 lg:p-10 text-sm text-muted-foreground">
          <span className="text-xs sm:text-sm">{currentVersion}</span>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-foreground transition-colors">
              Terms of Service
            </a>
            <a href="#" className="hover:text-foreground transition-colors">
              Privacy Policy
            </a>
          </div>
        </footer>
      </div>

      <div className="hidden w-1/2 md:block relative overflow-hidden">
        <div className={`absolute inset-0 ${baseBackgroundClass}`} />

        <AnimatedGradient
          colors={gradientColors}
          speed={45}
          blur="heavy"
        />

        <div className="absolute inset-0 halftone-overlay pointer-events-none" />
      </div>
    </div>
  );
}
