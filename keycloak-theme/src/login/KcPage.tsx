import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
const UserProfileFormFields = lazy(
  () => import("keycloakify/login/UserProfileFormFields")
);
const Login = lazy(() => import("./pages/Login"));
const Error = lazy(() => import("./pages/Error"));
const Info = lazy(() => import("./pages/Info"));
const LoginUpdatePassword = lazy(() => import("./pages/LoginUpdatePassword"));
const LoginUpdateProfile = lazy(() => import("./pages/LoginUpdateProfile"));
const LoginResetPassword = lazy(() => import("./pages/LoginResetPassword"));
const DeleteAccountConfirm = lazy(() => import("./pages/DeleteAccountConfirm"));

import "./main.css";

const doMakeUserConfirmPassword = true;

export default function KcPage(props: { kcContext: KcContext }) {
  const { kcContext } = props;

  const { i18n } = useI18n({ kcContext });

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case "login.ftl":
            return (
              <Login
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "error.ftl":
            return (
              <Error
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "info.ftl":
            return (
              <Info
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "login-update-password.ftl":
            return (
              <LoginUpdatePassword
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "login-update-profile.ftl":
            return (
              <LoginUpdateProfile
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "login-reset-password.ftl":
            return (
              <LoginResetPassword
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          case "delete-account-confirm.ftl":
            return (
              <DeleteAccountConfirm
                {...{ kcContext, i18n, classes }}
                Template={Template}
                doUseDefaultCss={false}
              />
            );
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                i18n={i18n}
                classes={classes}
                Template={Template}
                doUseDefaultCss={true}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
              />
            );
        }
      })()}
    </Suspense>
  );
}

const classes = {} satisfies { [key in ClassKey]?: string };
