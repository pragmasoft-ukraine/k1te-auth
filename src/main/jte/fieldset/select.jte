@import io.micronaut.views.fields.elements.SelectFormElement
@param SelectFormElement el
@param boolean multipleSelect = false
@template.fieldset.label(el.id(), el.label())
@if(el.hasErrors())
    @if(multipleSelect)
        <select name="${el.name()}"
                id="${el.id()}"
                class="form-select is-invalid"
                aria-describedby="${el.name()}ValidationServerFeedback"
                required="${el.required()}"
                multiple size="6">
            @for(var option : el.options())
                @template.fieldset.option(option)
            @endfor
        </select>
    @else
        <select name="${el.name()}"
                id="${el.id()}"
                class="form-select is-invalid"
                aria-describedby="${el.name()}ValidationServerFeedback"
                required="${el.required()}">
            @for(var option : el.options())
                @template.fieldset.option(option)
            @endfor
        </select>
    @endif
    @template.fieldset.errors(el.name(), el.errors())
@else
    @if(multipleSelect)
        <select name="${el.name()}"
                id="${el.id()}"
                class="form-select"
                required="${el.required()}"
                multiple size="6">
            @for(var option : el.options())
                @template.fieldset.option(option)
            @endfor
        </select>
    @else
        <select name="${el.name()}"
                id="${el.id()}"
                class="form-select"
                required="${el.required()}">
            @for(var option : el.options())
                @template.fieldset.option(option)
            @endfor
        </select>
    @endif
@endif
