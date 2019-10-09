# Application Permissions Reference

<table border="1">
    <tr>
        <th>State \ Role</th>
        <th>:applicant</th>
        <th>:commenter</th>
        <th>:decider</th>
        <th>:everyone-else</th>
        <th>:handler</th>
        <th>:member</th>
        <th>:past-commenter</th>
        <th>:past-decider</th>
        <th>:reporter</th>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/draft</th>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/change-resources</div>
            <div>:application.command/close</div>
            <div>:application.command/copy-as-new</div>
            <div>:application.command/invite-member</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/save-draft</div>
            <div>:application.command/submit</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top">
            <div>:application.command/accept-invitation</div>
        </td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/submitted</th>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/comment</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/decide</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-invitation</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/add-licenses</div>
            <div>:application.command/add-member</div>
            <div>:application.command/approve</div>
            <div>:application.command/change-resources</div>
            <div>:application.command/close</div>
            <div>:application.command/invite-member</div>
            <div>:application.command/reject</div>
            <div>:application.command/remark</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/request-comment</div>
            <div>:application.command/request-decision</div>
            <div>:application.command/return</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/returned</th>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/change-resources</div>
            <div>:application.command/close</div>
            <div>:application.command/copy-as-new</div>
            <div>:application.command/invite-member</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/save-draft</div>
            <div>:application.command/submit</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/comment</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/decide</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-invitation</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/add-licenses</div>
            <div>:application.command/add-member</div>
            <div>:application.command/change-resources</div>
            <div>:application.command/close</div>
            <div>:application.command/invite-member</div>
            <div>:application.command/remark</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/request-comment</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/approved</th>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/comment</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/decide</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-invitation</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/add-member</div>
            <div>:application.command/change-resources</div>
            <div>:application.command/close</div>
            <div>:application.command/invite-member</div>
            <div>:application.command/remark</div>
            <div>:application.command/remove-member</div>
            <div>:application.command/uninvite-member</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/accept-licenses</div>
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/closed</th>
        <td style="vertical-align: top">
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
    <tr>
        <th style="text-align: left; vertical-align: top">:application.state/rejected</th>
        <td style="vertical-align: top">
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top"></td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
        </td>
        <td style="vertical-align: top">
            <div>:application.command/copy-as-new</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
        <td style="vertical-align: top">
            <div>:see-everything</div>
            <div>:application.command/remark</div>
        </td>
    </tr>
</table>