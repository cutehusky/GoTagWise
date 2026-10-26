package smoke

type WrongReferences struct {
	ReferenceBase
	Missing   string             `validate:"eqfield=Absent"`
	Kind      int                `validate:"eqfield=Text"`
	Number    int                `validate:"gtefield=Long"`
	Condition string             `validate:"required_if=Age wrong"`
	Boolean   string             `validate:"excluded_if=Enabled yes"`
	Odd       string             `validate:"required_if=Age"`
	Repeated  string             `validate:"required_if=Mode x Mode y"`
	Presence  string             `validate:"required_with=Absent"`
	Nested    string             `validate:"eqcsfield=Address.Missing"`
	Index     string             `validate:"eqcsfield=Fixed[4].Code"`
	Contains  string             `validate:"fieldcontains=Age"`
	Unique    []ReferenceAddress `validate:"unique=Absent"`
}

type CorrectReferences struct {
	ReferenceBase
	Confirm       string             `validate:"eqfield=Text"`
	Number        int                `validate:"gtfield=Age"`
	Condition     string             `validate:"required_if=Enabled true"`
	ModeCondition string             `validate:"required_if=Mode 'New York'"`
	Presence      string             `validate:"required_with=Age"`
	Nested        string             `validate:"eqcsfield=Address.Code"`
	Index         string             `validate:"eqcsfield=Fixed[0].Code"`
	Unique        []ReferenceAddress `validate:"unique=Code"`
}
