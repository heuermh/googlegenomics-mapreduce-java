/*
Copyright 2014 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

$(function() {
    $.validator.addMethod(
        "end_greater_than_start",
        function(value, element) {
            return parseInt($('#end').val()) >= parseInt($('#start').val())
        },
        "End must be greater than or equal to start.");
    $.validator.addMethod(
        "start_less_than_end",
        function(value, element) {
            return parseInt($('#start').val()) <= parseInt($('#end').val())
        },
        "Start must be less than or equal to end.");
    $.validator.addMethod(
        "start_greater_than_zero",
        function(value, element) {
            return parseInt($('#start').val()) > 0
        },
        "Start must be greater than 0.");
    $("#coverageForm").validate({
        rules: {
            datasetId: "required",
            contig: "required",
            start: {
                required: true,
                number: true,
                start_greater_than_zero: true,
                start_less_than_end: true
            },
            end: {
                required: true,
                number: true,
                end_greater_than_start: true
            }
        },
        messages: {
            datasetId: "Please provide a Dataset Id.",
            contig: "Please provide a Contig.",
            start: {
                required: "Please provide a Start position."
            },
            end: {
                required: "Please provide an End position."
            }
         },
        submitHandler: function(form) {
            form.submit();
        }
    });
});
