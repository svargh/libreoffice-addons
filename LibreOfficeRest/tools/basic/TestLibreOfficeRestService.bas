REM LibreOffice Basic diagnostic macro for LibreOfficeRest.
REM Install the extension, then run this macro from LibreOffice.

Sub TestLibreOfficeRestService()
    On Error GoTo Bad

    Dim o As Object
    o = CreateUnoService("org.libreoffice.rest.LibreOfficeRest")

    If IsNull(o) Then
        MsgBox "CreateUnoService returned Null. Java component was not registered/loaded."
        Exit Sub
    End If

    MsgBox "Service object created: " & TypeName(o)
    MsgBox "PING result: " & o.PING(Null)
    MsgBox "JSONVALID(asd) result: " & o.JSONVALID(Null, "asd")
    MsgBox "JSONVALID({""a"":1}) result: " & o.JSONVALID(Null, "{""a"":1}")
    Exit Sub

Bad:
    MsgBox "LibreOfficeRest Basic diagnostic failed" & Chr(10) _
        & "Err: " & Err & Chr(10) _
        & "Type: " & TypeName(o) & Chr(10) _
        & "Message: " & Error$
End Sub
