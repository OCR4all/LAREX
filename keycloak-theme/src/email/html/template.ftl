<#--
  This file has been claimed for ownership from @keycloakify/email-native version 260007.0.0.
  To relinquish ownership and restore this file to its original content, run the following command:
  
  $ npx keycloakify own --path "email/html/template.ftl" --revert
-->

<#macro emailLayout>
<#assign themeResourcesUrl="">
<#if url?? && url.resourcesUrl??>
    <#assign themeResourcesUrl = url.resourcesUrl>
</#if>
<html lang="${(locale.currentLanguageTag)!'en'}">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="color-scheme" content="light only" />
    <meta name="supported-color-schemes" content="light only" />
    <title>${realmName!'LAREX'}</title>
    <style type="text/css">
        body {
            margin: 0;
            padding: 0;
            background-color: #f7f6f3;
        }

        table {
            border-collapse: collapse;
            border-spacing: 0;
        }

        img {
            border: 0;
            display: block;
            line-height: 100%;
            outline: none;
            text-decoration: none;
        }

        .email-body {
            color: #33332f;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            font-size: 16px;
            line-height: 1.65;
        }

        .email-body p {
            margin: 0 0 16px;
        }

        .email-body p:last-child {
            margin-bottom: 0;
        }

        .email-body a {
            color: #e1552e;
        }

        .email-body strong,
        .email-body b {
            color: #1c1c19;
        }

        .email-body code {
            background-color: #f0eee6;
            border-radius: 6px;
            color: #1c1c19;
            display: inline-block;
            font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
            padding: 2px 6px;
        }

        @media only screen and (max-width: 640px) {
            .email-shell {
                width: 100% !important;
            }

            .email-shell-padding {
                padding: 24px 12px !important;
            }

            .email-card-body {
                padding: 24px 20px !important;
            }
        }
    </style>
</head>
<body bgcolor="#f7f6f3" style="margin:0; padding:0; background-color:#f7f6f3;">
    <table role="presentation" width="100%" bgcolor="#f7f6f3" style="width:100%; background-color:#f7f6f3;">
        <tr>
            <td align="center" class="email-shell-padding" style="padding:32px 16px;">
                <table role="presentation" width="100%" class="email-shell" style="width:100%; max-width:600px;">
                    <tr>
                        <td style="padding-bottom:16px;">
                            <table
                                role="presentation"
                                width="100%"
                                bgcolor="#ffffff"
                                style="width:100%; background-color:#ffffff; border:1px solid #d5d2c5; border-top:4px solid #e1552e; border-radius:6px;"
                            >
                                <tr>
                                    <td class="email-card-body" style="padding:32px 32px 24px;">
                                        <table role="presentation" width="100%" style="width:100%;">
                                            <tr>
                                                <td style="padding-bottom:24px; border-bottom:1px solid #f0eee6;">
                                                    <table role="presentation" style="width:auto;">
                                                        <tr>
                                                            <#if themeResourcesUrl?has_content>
                                                                <td style="padding-right:12px; vertical-align:middle;">
                                                                    <img
                                                                        src="${themeResourcesUrl}/img/larex-logo.png"
                                                                        alt="LAREX"
                                                                        width="32"
                                                                        height="32"
                                                                        style="display:block; width:32px; height:32px; border:0;"
                                                                    />
                                                                </td>
                                                            </#if>
                                                            <td style="vertical-align:middle;">
                                                                <div style="color:#1c1c19; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; font-size:18px; font-weight:700; letter-spacing:0.04em;">
                                                                    LAREX
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td class="email-body" style="padding-top:24px; color:#33332f; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; font-size:16px; line-height:1.65; word-break:break-word;">
                                                    <#nested>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td align="center" style="color:#7e7c74; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; font-size:13px; line-height:1.5; padding:0 16px;">
                            Sent by ${realmName!'LAREX'}
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
</#macro>
