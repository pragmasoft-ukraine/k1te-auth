@import java.util.List
@import io.micronaut.views.fields.Form
@import io.micronaut.views.fields.messages.Message

@param Form form
@param List<Message> errors

@template.layout(title = "Change password",
body = @`
    <main class="container">
        <article>
            <div>
                <h1>Change password</h1>
                @template.fieldset.errors(errors = errors)
                @template.fieldset.form(form = form)
                <a href="/profile" role="button" class="outline contrast wide">Cancel</a>
            </div>
        </article>
    </main>

    <script type="text/javascript" src="/js/validation.js"></script>
`)
