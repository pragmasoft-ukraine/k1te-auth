@import java.util.List
@import io.micronaut.views.fields.Form
@import io.micronaut.views.fields.messages.Message

@param String siteKey
@param List<Message> errors
@param Form form

@template.layout(title = "Registration",
scripts = @`
    <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
`,
body = @`
    <main class="container">
        <article>
            <div>
                <h1>Registration</h1>
                @template.fieldset.errors(errors = errors)
                @template.fieldset.form(form = form, useCaptcha = true, siteKey = siteKey, useTurboDrive = false)
                <label align="center">
                    Already have an account? <a href="/auth/login" data-turbo="false">Login</a>
                </label>
            </div>
        </article>
    </main>

    <script type="text/javascript" src="/js/validation.js"></script>
`)
