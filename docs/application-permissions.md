# Application Permissions Reference

<table border="1">
    <tr>
        <th>State \ Role</th>
        <th>applicant</th>
        <th>decider</th>
        <th>everyone‑else</th>
        <th>handler</th>
        <th>member</th>
        <th>past‑decider</th>
        <th>past‑reviewer</th>
        <th>reporter</th>
        <th>reviewer</th>
    </tr>
    <tr>
        <th valign="top">approved</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
            <div>remove‑member</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
            <div>decide</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
            <div>accept‑invitation</div>
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>add‑member</div>
            <div>change‑resources</div>
            <div>close</div>
            <div>invite‑member</div>
            <div>remark</div>
            <div>remove‑member</div>
            <div>revoke</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
            <div>review</div>
        </td>
    </tr>
    <tr>
        <th valign="top">closed</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
        </td>
    </tr>
    <tr>
        <th valign="top">draft</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>accept‑licenses</div>
            <div>change‑resources</div>
            <div>copy‑as‑new</div>
            <div>delete</div>
            <div>invite‑member</div>
            <div>remove‑member</div>
            <div>save‑draft</div>
            <div>submit</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
            <div>accept‑invitation</div>
        </td>
        <td valign="top">
            <!-- role: handler -->
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
        </td>
    </tr>
    <tr>
        <th valign="top">rejected</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
        </td>
    </tr>
    <tr>
        <th valign="top">returned</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>accept‑licenses</div>
            <div>change‑resources</div>
            <div>close</div>
            <div>copy‑as‑new</div>
            <div>invite‑member</div>
            <div>remove‑member</div>
            <div>save‑draft</div>
            <div>submit</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
            <div>decide</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
            <div>accept‑invitation</div>
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>add‑licenses</div>
            <div>add‑member</div>
            <div>assign‑external‑id</div>
            <div>change‑resources</div>
            <div>close</div>
            <div>invite‑actor</div>
            <div>invite‑member</div>
            <div>remark</div>
            <div>remove‑member</div>
            <div>request‑review</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
            <div>review</div>
        </td>
    </tr>
    <tr>
        <th valign="top">revoked</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
        </td>
    </tr>
    <tr>
        <th valign="top">submitted</th>
        <td valign="top">
            <!-- role: applicant -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
            <div>remove‑member</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: decider -->
            <div>see‑everything</div>
            <div>approve</div>
            <div>decide</div>
            <div>reject</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: everyone-else -->
            <div>accept‑invitation</div>
        </td>
        <td valign="top">
            <!-- role: handler -->
            <div>see‑everything</div>
            <div>add‑licenses</div>
            <div>add‑member</div>
            <div>approve</div>
            <div>assign‑external‑id</div>
            <div>change‑resources</div>
            <div>close</div>
            <div>invite‑actor</div>
            <div>invite‑member</div>
            <div>reject</div>
            <div>remark</div>
            <div>remove‑member</div>
            <div>request‑decision</div>
            <div>request‑review</div>
            <div>return</div>
            <div>uninvite‑member</div>
        </td>
        <td valign="top">
            <!-- role: member -->
            <div>accept‑licenses</div>
            <div>copy‑as‑new</div>
        </td>
        <td valign="top">
            <!-- role: past-decider -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: past-reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
        </td>
        <td valign="top">
            <!-- role: reporter -->
            <div>see‑everything</div>
        </td>
        <td valign="top">
            <!-- role: reviewer -->
            <div>see‑everything</div>
            <div>remark</div>
            <div>review</div>
        </td>
    </tr>
</table>