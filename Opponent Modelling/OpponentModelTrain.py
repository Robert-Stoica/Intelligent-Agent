import pandas as pd
from lxml import etree
import numpy as np
import os
from datetime import datetime

from sklearn.model_selection import train_test_split
from keras.models import Sequential
from keras.layers import LSTM, Dense

# Hyperparameters
filepath = "genius-10.4/log"
sequence_length = 10


def process_row(entry):
    string = entry[4]
    string = string.split('[')[1]
    string = string.split(']')[0]

    split_string = string.split(":")

    strings = []
    for stringed in split_string:
        strings.append(stringed.rsplit(',', 1))

    flattened_list = [item.strip() for sublist in strings for item in sublist]

    formatted_list = [f'{flattened_list[i]}: {flattened_list[i + 1]}' for i in range(0, len(flattened_list), 2)]
    return pd.Series(formatted_list)


def weights_and_values(xml_file):
    tree = etree.parse(xml_file)
    root = tree.getroot()

    dicts = {}
    index_names = {}
    for issue in root.xpath(".//issue"):
        issue_name = issue.get("name")
        issue_index = issue.get("index")
        # print(f"\nEvaluations for {issue_name}({issue_index}):")
        index_names[issue_name] = issue_index

        dictionary = {}
        if issue.get("vtype") == "discrete":
            for item in issue.xpath(".//item"):
                item_index = item.get("index")
                evaluation = item.get("evaluation")
                title = item.get("value")
                dictionary[title] = evaluation
        # Incomplete code but doesn't matter as only discrete issues assessed
        else:
            evaluator = issue.xpath(".//evaluator")[0]
            for i in range(int(issue.get("lowerbound")), int(issue.get("upperbound"))):
                dictionary[str(1)] = float(evaluator.get("slope")) * i + float(evaluator.get("offset"))
            dictionary[issue.get("upperbound")] = str(1)
        # print(dictionary)
        dicts[issue_index] = dictionary

    """
    print()
    print(index_names)

    print()
    print("Weight values:")
    """
    weight_dict = {}
    for weight in root.xpath(".//weight"):
        weight_index = weight.get("index")
        weight_value = weight.get("value")
        weight_dict[weight_index] = weight_value
    # print(weight_dict)

    return dicts, index_names, weight_dict


def weighted_sums(data, dicts, index_names, weight_dict):
    evaluations = []
    for index, row in data.iterrows():
        sum_value = 0
        for column, value in row.items():
            val_index_indent = value.split(": ")[0].strip()
            val_val = value.split(": ")[1].strip()

            val_index = index_names.get(val_index_indent)
            working_dict = dicts.get(val_index)

            evals = working_dict.get(val_val)
            mul_weight = weight_dict.get(val_index)

            sum_value += float(evals) * float(mul_weight)
        evaluations.append(sum_value)
    return evaluations


def matches_original(offers_list):
    match_data = []
    for index, row in offers_list.iterrows():
        matches = (row == offers_list.iloc[0]).sum() / offers_list.shape[1]
        match_data.append(matches)

    return match_data


def create_sequence(X, y):
    # Create sequences with the specified length
    X_sequences = []
    y_targets = []

    for i in range(len(X) - sequence_length + 1):
        X_sequences.append(X[i: i + sequence_length])
        y_targets.append(y[i + sequence_length - 1])

    X_sequences = np.array(X_sequences)
    y_targets = np.array(y_targets)

    return X_sequences, y_targets


def preprocess_file(csv, xml_log):
    df = pd.read_csv(csv, header=None, sep=r',(?!\s)', engine='python')
    df = df.drop(df.index[-1])

    # Apply the function to the DataFrame column
    formatted_df = df.apply(lambda entry: process_row(entry), axis=1)

    # Concatenate the result with the original DataFrame
    df = pd.concat([df.iloc[:, 2], formatted_df], axis=1)

    offers_first = df[df.index % 2 == 0].reset_index(drop=True)
    offers_second = df[df.index % 2 != 0].reset_index(drop=True)

    # Get the weights and values from the XML file
    origin_tree = etree.parse(xml_log)
    origin_root = origin_tree.getroot()
    utilspace_list = [results.get("utilspace") for results in origin_root.xpath(".//resultsOfAgent")]

    agent_1_xml = "genius-10.4/" + utilspace_list[0]
    agent_2_xml = "genius-10.4/" + utilspace_list[1]

    # Combination
    offers_first_rounds = offers_first.iloc[:, :1]
    offers_second_rounds = offers_second.iloc[:, :1]

    offers_first_data = offers_first.iloc[:, 1:]
    offers_second_data = offers_second.iloc[:, 1:]

    p1_dicts, p1_index_names, p1_weight_dict = weights_and_values(agent_1_xml)
    p2_dicts, p2_index_names, p2_weight_dict = weights_and_values(agent_2_xml)

    first_evals_p1 = weighted_sums(offers_first_data, p1_dicts, p1_index_names, p1_weight_dict)
    second_evals_p1 = weighted_sums(offers_second_data, p1_dicts, p1_index_names, p1_weight_dict)

    first_evals_p2 = weighted_sums(offers_first_data, p2_dicts, p2_index_names, p2_weight_dict)
    second_evals_p2 = weighted_sums(offers_second_data, p2_dicts, p2_index_names, p2_weight_dict)

    p1_matches = matches_original(offers_first_data)
    p2_matches = matches_original(offers_second_data)

    # Output in correct shape
    p1_eval_of_p2 = np.array(second_evals_p1)
    p2_similarity = np.array(p2_matches)
    round_time = np.array(offers_first_rounds)

    p2_eval_of_p1 = np.array(first_evals_p2)
    p1_similarity = np.array(p1_matches)
    round_time_2 = np.array(offers_second_rounds)

    X_1 = np.column_stack((p1_eval_of_p2, p2_similarity, round_time_2))
    y_1 = np.array(second_evals_p2)

    X_2 = np.column_stack((p2_eval_of_p1, p1_similarity, round_time))
    y_2 = np.array(first_evals_p1)

    X_1_sequence, y_1_targets = create_sequence(X_1, y_1)
    X_2_sequence, y_2_targets = create_sequence(X_2, y_2)

    temp_X = np.concatenate((X_1_sequence, X_2_sequence), axis=0)
    temp_y = np.concatenate((y_1_targets, y_2_targets), axis=0)

    return temp_X, temp_y


X_list = []
y_list = []

for filename in os.listdir(filepath):
    if filename.endswith('.csv'):
        csv_file_path = os.path.join(filepath, filename)

        # Construct the corresponding XML file path
        xml_filename = os.path.splitext(filename)[0] + '.xml'
        xml_file_path = os.path.join(filepath, xml_filename)

        if os.path.exists(xml_file_path):
            print(f"Processing {xml_filename}")
            X_temp, y_temp = preprocess_file(csv_file_path, xml_file_path)
            if len(X_temp) > 0:
                X_list.append(X_temp)
                y_list.append(y_temp)

X = np.concatenate(X_list, axis=0)
y = np.concatenate(y_list, axis=0)

# Split the data into training and testing sets
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# Build the LSTM model
model = Sequential()
model.add(LSTM(units=50, activation='relu', input_shape=(sequence_length, X.shape[2])))
model.add(Dense(units=1))  # Adjust the number of units based on your problem (regression/classification)

# Compile the model
model.compile(optimizer='adam', loss='mean_squared_error', metrics=['mae'])

# Train the model
model.fit(X_train, y_train, epochs=10, batch_size=32, validation_data=(X_test, y_test))

# Evaluate the model
loss = model.evaluate(X_test, y_test)
print(f"Test Loss: {loss}")

timestamp = datetime.now().strftime("%Y-%m-%d_%H%M")
model.save(f"genius-10.4/Models/model_{timestamp}")
