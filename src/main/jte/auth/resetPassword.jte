@import java.util.List
@import io.micronaut.views.fields.Form
@import io.micronaut.views.fields.messages.Message

@param List<Message> errors
@param Form form

@template.layout(title = "Reset password",
body = @`
    <main class="container">
        <article>
            <div>
                <h1>Reset password</h1>
                @template.fieldset.errors(errors = errors)
                @template.fieldset.form(form = form)
                <a href="/auth/forgot-password" role="button" class="outline contrast wide">Cancel</a>
            </div>
        </article>
    </main>

    <script type="text/javascript" src="/js/validation.js"></script>
`)
