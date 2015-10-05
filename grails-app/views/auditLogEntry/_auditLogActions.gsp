<!-- This template renders a drop down after a controller is selected -->
<g:select name="usedAction"
          from="${actions}"
          value="${choosenAction}"
          optionValue="${ { name -> message(code: "${controllerName}.${name}") } }"
          noSelection="${['':g.message(code: "default.select.no.select", default: "Vælg en")]}"/>
