@import pragmasoft.k1teauth.user.User

@param User user

@template.layout(title = "Profile",
body = @`
    <h4>Hello, ${user.getFullName()} &#x1F44B;</h4>
    <h4>Email: ${user.getEmail()}</h4>
    <a href="/profile/change-password" role="button" class="outline contrast">Change password</a>
    <a href="/granted-consents" role="button" class="outline contrast">Granted consents</a>
    @if (user.isAdmin())
        <hr>
        <a href="/admin/users-list" role="button" class="outline contrast">Users list</a>
        <a href="/oauth2/clients" role="button" class="outline contrast">OAuth Clients</a>
        <a href="/oauth2/scopes" role="button" class="outline contrast">OAuth Scopes</a>
    @endif
    <form action="/logout" class="logout-form" method="post" data-turbo="false">
        <button type="submit" class="outline">Logout</button>
    </form>
`)