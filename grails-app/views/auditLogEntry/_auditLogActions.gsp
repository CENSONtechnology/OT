<!-- This template renders a drop down after a controller is selected -->
<g:select name="chosenAction"
          from="${actions}"
          selected="${chosenAction}"
          value="${chosenAction}"
          optionValue="${ { name -> message(code: "${chosenController}.${name}") } }"
          noSelection="${['':g.message(code: "default.select.no.select", default: "Vælg en")]}"/>
