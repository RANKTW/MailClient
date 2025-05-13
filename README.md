# MailClient
Open-source email client that manages all your email accounts in one place for Windows, Mac, and Linux. 

Supports: `Microsoft Graph API`, `Microsoft IMAP OAuth2`, and `IMAP Basic Authentication` for other common email providers.

You can add your custom IMAP hostname in the `hosts.json` file.

✅ Image Tracker Protection<br>
<img height="550" src="https://i.imgur.com/d1BbEbZ.png"/>

---
## `proxies.txt`: Multiple Input Formats Supported
```
hostname:port
hostname:port:username:password
username:password@hostname:port
```
Need high-quality cheap proxies?
[https://tokenu.to](https://tokenu.to/?utm_source=github&utm_medium=MailClient)

## `emails.txt`: Multiple Input Formats Supported
#### JsonArray format
###### for Microsoft `Graph API` or `IMAP OAuth2`
```
[{email,password,clientId,refreshToken}, {email,password,clientId,refreshToken}]
```
---
#### JsonObject List format (separated by new line)
###### for Microsoft `Graph API` or `IMAP OAuth2`
```
{email,password,clientId,refreshToken}
{email,password,clientId,refreshToken}
{email,password,clientId,refreshToken}
```
---
#### Colon Separated format
###### for other common email providers that use `IMAP Basic Authentication`
###### such as mail.com, gmx.com, rambler.ru, mail.ru. See all in `hosts.json`
```
email:password
email:password
email:password
```
---
Made by [TOKENU.NET](https://www.tokenu.net/?utm_source=github&utm_medium=MailClient)
